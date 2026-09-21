package org.akanework.gramophone.ui.components.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.session.MediaController
import androidx.preference.PreferenceManager
import com.google.android.material.motion.MaterialBottomContainerBackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.LyricsView
import org.akanework.gramophone.ui.components.NowPlayingController
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import uk.akane.libphonograph.manipulator.PlaylistSerializer

class PlayerSheetViewImpl private constructor(
    context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes),
    Player.Listener, DefaultLifecycleObserver {
    constructor(context: Context, attributeSet: AttributeSet?)
            : this(context, attributeSet, 0, 0)

    companion object {
        private const val TAG = "PlayerBottomSheet"
        private const val STATE_SUPER = "super"
        private const val STATE_EXPANDED = "expanded"
        private const val STATE_FRACTION = "fraction"
        private const val STATE_POSITION = "position"
        private const val STATE_DURATION = "duration"
    }

    @SuppressLint("RestrictedApi")
    private var lyricsBackHelper: MaterialBottomContainerBackHelper? = null
    private var bottomSheetBackCallback: OnBackPressedCallback? = null

    // The whole morph is driven by this single 0..1 progress. The scope
    // uses AndroidUiDispatcher.Main because Compose's animate() needs the MonotonicFrameClock it
    // carries (as rememberCoroutineScope() would provide inside a composition)
    private val sheetScope = CoroutineScope(AndroidUiDispatcher.Main)
    private val sheetState = NowPlayingSheetState(sheetScope)
    private val playerState = PlayerSheetPlayerState()
    private val chromeState =
        mutableStateOf(SheetChrome(shown = false, left = 0, right = 0, top = 0, bottom = 0))

    private val fullContainer: View
    private val lyricsView: LyricsView
    private val nowPlaying: NowPlayingController
    private val composeView: ComposeView

    private val activity
        get() = context as MainActivity
    private val lifecycleOwner: LifecycleOwner
        get() = activity
    private val instance: MediaController?
        get() = activity.getPlayer()
    private var lastActuallyVisible: Boolean? = null
    private var lastMeasuredHeight: Int? = null
    private var pendingExpanded = false

    var visible = false
        set(value) {
            if (field != value) {
                field = value
                refreshVisibility()
            }
        }
    val actuallyVisible: Boolean
        get() = chromeState.value.shown
    val visibleAndExpanded: Boolean
        get() = chromeState.value.shown && sheetState.expandedTarget

    // Full player and controller wiring
    private val fullPlayerActions = FullPlayerActions(
        playPause = { instance?.playOrPause() },
        previous = { instance?.seekToPrevious() },
        next = { instance?.seekToNext() },
        seekBack = { instance?.seekBack() },
        seekForward = { instance?.seekForward() },
        seekTo = { ms -> instance?.seekTo(ms) },
        minimize = { sheetState.collapse() },
        cycleRepeat = {
            instance?.let { c ->
                c.repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        },
        toggleShuffle = { on -> instance?.shuffleModeEnabled = on },
        toggleFavorite = { on ->
            instance?.currentMediaItem?.let { song ->
                PlaylistSerializer.Entry.ofMediaItem(song)?.let { activity.markIsFavoriteStatus(listOf(it), on) }
            }
        },
        showQueue = { nowPlaying.showQueue() },
        showLyrics = { lyricsView.fadInAnimation(PlayerUtilities.LYRIC_COVER_FADE_MS.toLong()) },
        openAlbum = { nowPlaying.openAlbumPage() },
        openArtist = { nowPlaying.openArtistPage() },
    )

    // Reads/writes the Compose timer & speed dialogs need (MediaController + prefs)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val dialogCallbacks = PlayerDialogCallbacks(
        currentSpeed = { instance?.playbackParameters?.speed ?: 1f },
        currentPitch = { instance?.playbackParameters?.pitch ?: 1f },
        setSpeedPitch = { s, p -> instance?.playbackParameters = PlaybackParameters(s, p) },
        timerRemainingMs = { instance?.getTimer()?.first?.toLong() },
        timerEndOfSong = { instance?.getTimer()?.second == true },
        setTimer = { d, eos -> instance?.setTimer(d, eos) },
        getBool = { key, def -> prefs.getBooleanStrict(key, def) },
        putBool = { key, value -> prefs.edit { putBoolean(key, value) } },
    )

    init {
        fullContainer = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_lyrics, this, false)
        lyricsView = fullContainer.findViewById(R.id.lyric_frame)!!
        lyricsView.onCoveringChanged = { covering -> playerState.lyricsCovering.value = covering }
        nowPlaying = NowPlayingController(
            activity = activity,
            lyricsView = lyricsView,
            minimize = { sheetState.collapse() },
            onQualityChanged = { icon, text ->
                playerState.qualityIcon.value = icon
                playerState.qualityText.value = text
            },
        )

        val pureDark = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean("pureDark", false)
        composeView = ComposeView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setContent {
                GramophoneTheme(pureDark = pureDark) {
                    PlayerSheet(
                        state = sheetState,
                        player = playerState,
                        chrome = chromeState,
                        fullPlayerView = fullContainer,
                        onPlayPause = { instance?.playOrPause() },
                        onNext = { instance?.seekToNext() },
                        onCoverClick = {
                            activity.startFragment(DetailDialogFragment()) {
                                putString("Id", instance?.currentMediaItem?.mediaId)
                            }
                        },
                        onExpandedTargetChanged = { expanded ->
                            bottomSheetBackCallback?.isEnabled = expanded
                        },
                        actions = fullPlayerActions,
                        dialogCallbacks = dialogCallbacks,
                    )
                }
            }
        }
        addView(composeView)

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            syncPlayerState()
            refreshVisibility()
            nowPlaying.refreshLyrics()
        }

        sheetScope.launch {
            while (isActive) {
                if (chromeState.value.shown) {
                    val inst = instance
                    val duration = inst?.duration?.takeIf { it > 0 }
                        ?: inst?.currentMediaItem?.mediaMetadata?.durationMs
                    if (inst != null && duration != null && duration > 0) {
                        val position = inst.currentPosition
                        playerState.positionMs.value = position
                        playerState.durationMs.value = duration
                        playerState.positionFraction.value =
                            (position.toFloat() / duration).coerceIn(0f, 1f)
                    }
                    val timer = inst?.getTimer()
                    playerState.timerActive.value = timer?.first != null || timer?.second == true
                    if (sheetState.expandedTarget) lyricsView.updateLyricPositionFromPlaybackPos()
                }
                delay(if (sheetState.expandedTarget) PlayerUtilities.FULL_POLL_MS else PlayerUtilities.POSITION_POLL_MS)
            }
        }
    }

    fun open() {
        if (chromeState.value.shown) {
            sheetState.expand()
        }
    }

    private fun syncPlayerState() {
        val item = instance?.currentMediaItem
        playerState.isPlaying.value = instance?.isPlaying == true
        playerState.title.value = item?.mediaMetadata?.title
        playerState.artist.value =
            item?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
        playerState.artworkUri.value = item?.mediaMetadata?.artworkUri
        playerState.repeatMode.value = instance?.repeatMode ?: Player.REPEAT_MODE_OFF
        playerState.shuffleMode.value = instance?.shuffleModeEnabled == true
        playerState.isFavorite.value =
            (item?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        playerState.repeatMode.value = repeatMode
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        playerState.shuffleMode.value = shuffleModeEnabled
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        playerState.isFavorite.value = (mediaMetadata.userRating as? HeartRating)?.isHeart == true
    }

    private fun refreshVisibility() {
        val hasMedia = (instance?.mediaItemCount ?: 0) > 0
        playerState.hasMedia.value = hasMedia
        val show = visible && hasMedia
        if (chromeState.value.shown != show) {
            chromeState.value = chromeState.value.copy(shown = show)
            if (!show) {
                sheetState.snapToCollapsed()
            } else if (pendingExpanded) {
                pendingExpanded = false
                sheetState.snapToExpanded()
            }
        }
        dispatchBottomSheetInsets()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playerState.isPlaying.value = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        playerState.isPlaying.value = instance?.isPlaying == true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout {
            @SuppressLint("RestrictedApi")
            lyricsBackHelper =
                MaterialBottomContainerBackHelper(lyricsView)
            bottomSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (lyricsView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.startBackProgress(backEvent)
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (lyricsView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.updateBackProgress(backEvent)
                    } else {
                        sheetState.onBackProgress(backEvent.progress)
                    }
                }

                override fun handleOnBackPressed() {
                    if (lyricsView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        val backEvent = lyricsBackHelper!!.onHandleBackInvoked()
                        if (backEvent == null || backEvent.progress == 0f
                            || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        ) {
                            lyricsView.fadOutAnimation(PlayerUtilities.LYRIC_COVER_FADE_MS.toLong())
                            return
                        }
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.finishBackProgressPersistent(
                            backEvent,
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationStart(animation: Animator) {
                                    lyricsView.fadOutAnimation(PlayerUtilities.LYRIC_COVER_FADE_MS.toLong())
                                }
                            })
                    } else {
                        sheetState.collapse()
                    }
                }

                override fun handleOnBackCancelled() {
                    if (lyricsView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.cancelBackProgress()
                    } else {
                        sheetState.expand()
                    }
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, bottomSheetBackCallback!!)
            bottomSheetBackCallback!!.isEnabled = sheetState.expandedTarget
            lifecycleOwner.lifecycle.addObserver(this)
            dispatchBottomSheetInsets()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastActuallyVisible = null
        lastMeasuredHeight = null
        nowPlaying.release()
        lifecycleOwner.lifecycle.removeObserver(this)
        bottomSheetBackCallback?.remove()
        sheetScope.cancel()
        onStop(lifecycleOwner)
    }

    override fun onSaveInstanceState(): Parcelable =
        Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putBoolean(STATE_EXPANDED, sheetState.expandedTarget)
            putFloat(STATE_FRACTION, playerState.positionFraction.value)
            putLong(STATE_POSITION, playerState.positionMs.value)
            putLong(STATE_DURATION, playerState.durationMs.value)
        }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            pendingExpanded = state.getBoolean(STATE_EXPANDED, false)
            playerState.positionFraction.value = state.getFloat(STATE_FRACTION, 0f)
            playerState.positionMs.value = state.getLong(STATE_POSITION, 0L)
            playerState.durationMs.value = state.getLong(STATE_DURATION, 0L)
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * The floating mini bar's total footprint from the screen bottom (px) — its platform above the
     * navigation bar plus the 56dp card — so lists get a bottom padding that clears it. Mirrors
     * PlayerSheetMetrics.collapsedFootprint (MINI_HEIGHT).
     */
    private fun collapsedHeightPx(): Int {
        val d = resources.displayMetrics.density
        val nav = chromeState.value.bottom
        val platform = maxOf(nav + (8 * d).toInt(), (24 * d).toInt())
        return platform + (56 * d).toInt()
    }

    fun getBottomPadding() = if (lastActuallyVisible == true) lastMeasuredHeight ?: 0 else 0

    private fun dispatchBottomSheetInsets() {
        val height = collapsedHeightPx()
        if (lastMeasuredHeight == height && lastActuallyVisible == actuallyVisible) return
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatching bottom sheet insets")

        lastMeasuredHeight = height
        lastActuallyVisible = actuallyVisible

        // Re-dispatch the last known insets to force regeneration of the FragmentContainerView's
        // insets, which give lists a bottom padding equal to the collapsed mini bar height.
        val i = ViewCompat.getRootWindowInsets(activity.window.decorView)
        if (i != null) {
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, i.clone())
        } else Log.e(TAG, "getRootWindowInsets returned null, this should NEVER happen")
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )

        // Feed the mini bar its safe-area insets
        chromeState.value = chromeState.value.copy(
            left = myInsets.left, right = myInsets.right, top = myInsets.top, bottom = myInsets.bottom
        )
        ViewCompat.dispatchApplyWindowInsets(lyricsView, insets.clone())

        if (isLaidOut && !isLayoutRequested) {
            dispatchBottomSheetInsets()
        } else {
            doOnNextLayout {
                dispatchBottomSheetInsets()
            }
        }

        val i = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, myInsets.top, 0, 0)
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, i.top, 0, 0)
            )
            .build()
            .toWindowInsets()!!
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        syncPlayerState()
        refreshVisibility()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        nowPlaying.onStop()
    }
}