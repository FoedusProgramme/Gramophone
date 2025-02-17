package org.akanework.gramophone.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.graphics.TypefaceCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.load
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.common.util.concurrent.Futures
import java.util.LinkedList
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getAudioFormat
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getLyricsLegacy
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioFormatInfo
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioQuality
import org.akanework.gramophone.logic.utils.AudioFormatDetector.SpatialFormat
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.ColorUtils
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId

@SuppressLint("NotifyDataSetChanged")
@androidx.annotation.OptIn(UnstableApi::class)
class FullBottomSheet
    (context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val activity
        get() = context as MainActivity
    private val instance: MediaController?
        get() = activity.getPlayer()
    var minimize: (() -> Unit)? = null

    private var wrappedContext: Context? = null
    private var currentJob: Job? = null
    private var isUserTracking = false
    private var runnableRunning = false
    private var firstTime = false

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val SLIDER_UPDATE_INTERVAL: Long = 100
        const val BACKGROUND_COLOR_TRANSITION_SEC: Long = 300
        const val FOREGROUND_COLOR_TRANSITION_SEC: Long = 150
        const val LYRIC_FADE_TRANSITION_SEC: Long = 125
    }

    private val touchListener =
        object : SeekBar.OnSeekBarChangeListener, Slider.OnSliderTouchListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dest = instance?.currentMediaItem?.mediaMetadata?.durationMs
                    if (dest != null) {
                        bottomSheetFullPosition.text =
                            CalculationUtils.convertDurationToTimeStamp((progress.toLong()))
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
                progressDrawable.animate = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val mediaId = instance?.currentMediaItem
                if (mediaId != null) {
                    if (seekBar != null) {
                        instance?.seekTo((seekBar.progress.toLong()))
                        bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
                    }
                }
                isUserTracking = false
                progressDrawable.animate =
                    instance?.isPlaying == true || instance?.playWhenReady == true
            }

            override fun onStartTrackingTouch(slider: Slider) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val mediaId = instance?.currentMediaItem
                if (mediaId != null) {
                    instance?.seekTo((slider.value.toLong()))
                    bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
                }
                isUserTracking = false
            }
        }
    private val bottomSheetFullCover: ImageView
    private val bottomSheetFullTitle: TextView
    private val bottomSheetFullSubtitle: TextView
    private val bottomSheetFullControllerButton: MaterialButton
    private val bottomSheetFullNextButton: MaterialButton
    private val bottomSheetFullPreviousButton: MaterialButton
    private val bottomSheetFullDuration: TextView
    private val bottomSheetFullPosition: TextView
    private var bottomSheetFullQualityDetails: TextView
    private val bottomSheetFullSlideUpButton: MaterialButton
    private val bottomSheetShuffleButton: MaterialButton
    private val bottomSheetLoopButton: MaterialButton
    private val bottomSheetPlaylistButton: MaterialButton
    private val bottomSheetTimerButton: MaterialButton
    private val bottomSheetFavoriteButton: MaterialButton
    val bottomSheetLyricButton: MaterialButton
    private val bottomSheetFullSeekBar: SeekBar
    private val bottomSheetFullSlider: Slider
    private val bottomSheetFullCoverFrame: MaterialCardView
    val bottomSheetFullLyricView: LyricsView
    private val progressDrawable: SquigglyProgress
    private var fullPlayerFinalColor: Int = -1
    private var colorPrimaryFinalColor: Int = -1
    private var colorSecondaryContainerFinalColor: Int = -1
    private var colorOnSecondaryContainerFinalColor: Int = -1
    private var colorContrastFaintedFinalColor: Int = -1
    private var playlistNowPlaying: TextView? = null
    private var playlistNowPlayingCover: ImageView? = null
    private var lastDisposable: Disposable? = null

    init {
        inflate(context, R.layout.full_player, this)
        bottomSheetFullCoverFrame = findViewById(R.id.album_cover_frame)
        bottomSheetFullCover = findViewById(R.id.full_sheet_cover)
        bottomSheetFullTitle = findViewById(R.id.full_song_name)
        bottomSheetFullSubtitle = findViewById(R.id.full_song_artist)
        bottomSheetFullPreviousButton = findViewById(R.id.sheet_previous_song)
        bottomSheetFullControllerButton = findViewById(R.id.sheet_mid_button)
        bottomSheetFullNextButton = findViewById(R.id.sheet_next_song)
        bottomSheetFullPosition = findViewById(R.id.position)
        bottomSheetFullDuration = findViewById(R.id.duration)
        bottomSheetFullSeekBar = findViewById(R.id.slider_squiggly)
        bottomSheetFullSlider = findViewById(R.id.slider_vert)
        bottomSheetFullSlideUpButton = findViewById(R.id.slide_down)
        bottomSheetShuffleButton = findViewById(R.id.sheet_random)
        bottomSheetLoopButton = findViewById(R.id.sheet_loop)
        bottomSheetTimerButton = findViewById(R.id.timer)
        bottomSheetFavoriteButton = findViewById(R.id.favor)
        bottomSheetPlaylistButton = findViewById(R.id.playlist)
        bottomSheetLyricButton = findViewById(R.id.lyrics)
        bottomSheetFullLyricView = findViewById(R.id.lyric_frame)
        bottomSheetFullQualityDetails = findViewById(R.id.quality_details)
        fullPlayerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface
        )
        colorPrimaryFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary
        )
        colorOnSecondaryContainerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSecondaryContainer
        )
        colorSecondaryContainerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetFullLyricView) { v, insets ->
            val myInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateMargin {
                left = -myInsets.left
                top = -myInsets.top
                right = -myInsets.right
                bottom = -myInsets.bottom
            }
            v.setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .build()
        }
        refreshSettings(null)
        prefs.registerOnSharedPreferenceChangeListener(this)
        activity.controllerViewModel.customCommandListeners.addCallback(activity.lifecycle) { _, command, _ ->
            when (command.customAction) {
                GramophonePlaybackService.SERVICE_TIMER_CHANGED -> updateTimer()

                GramophonePlaybackService.SERVICE_GET_LYRICS -> {
                    if (prefs.getBoolean("lyric_parser", false)) {
                        val parsedLyrics = instance?.getLyrics()
                        bottomSheetFullLyricView.updateLyrics(parsedLyrics)
                    } else {
                        val parsedLyrics = instance?.getLyricsLegacy()
                        bottomSheetFullLyricView.updateLyricsLegacy(parsedLyrics)
                    }
                }

                GramophonePlaybackService.SERVICE_GET_AUDIO_FORMAT -> {
                    val format = instance?.getAudioFormat()
                    updateQualityIndicators(format)
                }

                else -> {
                    return@addCallback Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }
            return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        val seekBarProgressWavelength =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_wavelength)
                .toFloat()
        val seekBarProgressAmplitude =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_amplitude)
                .toFloat()
        val seekBarProgressPhase =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_phase)
                .toFloat()
        val seekBarProgressStrokeWidth =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_stroke_width)
                .toFloat()

        bottomSheetFullSeekBar.progressDrawable = SquigglyProgress().also {
            progressDrawable = it
            it.waveLength = seekBarProgressWavelength
            it.lineAmplitude = seekBarProgressAmplitude
            it.phaseSpeed = seekBarProgressPhase
            it.strokeWidth = seekBarProgressStrokeWidth
            it.transitionEnabled = true
            it.animate = false
            it.setTint(
                MaterialColors.getColor(
                    bottomSheetFullSeekBar,
                    com.google.android.material.R.attr.colorPrimary,
                )
            )
        }

        bottomSheetFullCover.setOnClickListener {
            activity.startFragment(DetailDialogFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaId)
            }
        }

        bottomSheetFullTitle.setOnClickListener {
            minimize?.invoke()
            activity.startFragment(GeneralSubFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaMetadata?.albumId?.toString())
                putInt("Item", R.id.album)
            }
        }

        bottomSheetFullSubtitle.setOnClickListener {
            minimize?.invoke()
            activity.startFragment(ArtistSubFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaMetadata?.artistId?.toString())
                putInt("Item", R.id.artist)
            }
        }

        bottomSheetTimerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            val picker =
                MaterialTimePicker
                    .Builder()
                    .setHour((instance?.getTimer() ?: 0) / 3600 / 1000)
                    .setMinute(((instance?.getTimer() ?: 0) % (3600 * 1000)) / (60 * 1000))
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .build()
            picker.addOnPositiveButtonClickListener {
                val destinationTime: Int = picker.hour * 1000 * 3600 + picker.minute * 1000 * 60
                instance?.setTimer(destinationTime)
            }
            picker.show(activity.supportFragmentManager, "timer")
        }

        bottomSheetLoopButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.repeatMode = when (instance?.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> throw IllegalStateException()
            }
        }

        /*
        bottomSheetFavoriteButton.addOnCheckedChangeListener { _, isChecked ->
            /*
            if (isChecked) {
                instance.currentMediaItem?.let { insertIntoPlaylist(it) }
            } else {
                instance.currentMediaItem?.let { removeFromPlaylist(it) }
            }
             */
        }

         */

        bottomSheetPlaylistButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            val playlistBottomSheet = BottomSheetDialog(context)
            playlistBottomSheet.setContentView(R.layout.playlist_bottom_sheet)
            val recyclerView = playlistBottomSheet.findViewById<MyRecyclerView>(R.id.recyclerview)!!
            ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, ic ->
                val i = ic.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                val i2 = ic.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.setPadding(i.left, 0, i.right, i.bottom)
                return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(ic)
                    .setInsets(
                        WindowInsetsCompat.Type.systemBars()
                                or WindowInsetsCompat.Type.displayCutout(),
                        Insets.of(0, i.top, 0, 0)
                    )
                    .setInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.systemBars()
                                or WindowInsetsCompat.Type.displayCutout(),
                        Insets.of(0, i2.top, 0, 0)
                    )
                    .build()
            }
            val playlistAdapter = PlaylistCardAdapter(activity)
            val callback: ItemTouchHelper.Callback =
                PlaylistCardMoveCallback(playlistAdapter::onRowMoved)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(recyclerView)
            playlistNowPlaying = playlistBottomSheet.findViewById(R.id.now_playing)
            playlistNowPlaying!!.text = instance?.currentMediaItem?.mediaMetadata?.title
            playlistNowPlayingCover = playlistBottomSheet.findViewById(R.id.now_playing_cover)
            playlistNowPlayingCover!!.load(instance?.currentMediaItem?.mediaMetadata?.artworkUri) {
                placeholderScaleToFit(R.drawable.ic_default_cover)
                crossfade(true)
                error(R.drawable.ic_default_cover)
            }
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = playlistAdapter
            recyclerView.scrollToPosition(playlistAdapter.playlist.first.indexOfFirst { i ->
                i == (instance?.currentMediaItemIndex ?: 0)
            })
            recyclerView.fastScroll(null, null)
            playlistBottomSheet.setOnDismissListener {
                if (playlistNowPlaying != null) {
                    playlistNowPlayingCover!!.dispose()
                    playlistNowPlayingCover = null
                    playlistNowPlaying = null
                }
            }
            playlistBottomSheet.show()
        }
        bottomSheetFullControllerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.playOrPause()
        }
        bottomSheetFullPreviousButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToPrevious()
        }
        bottomSheetFullNextButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToNext()
        }
        bottomSheetShuffleButton.addOnCheckedChangeListener { _, isChecked ->
            instance?.shuffleModeEnabled = isChecked
        }

        bottomSheetFullSlider.addOnChangeListener { _, value, isUser ->
            if (isUser) {
                val dest = instance?.currentMediaItem?.mediaMetadata?.durationMs
                if (dest != null) {
                    bottomSheetFullPosition.text =
                        CalculationUtils.convertDurationToTimeStamp((value).toLong())
                }
            }
        }

        bottomSheetFullSeekBar.setOnSeekBarChangeListener(touchListener)
        bottomSheetFullSlider.addOnSliderTouchListener(touchListener)

        bottomSheetFullSlideUpButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            minimize?.invoke()
        }

        bottomSheetLyricButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            bottomSheetFullLyricView.fadInAnimation(LYRIC_FADE_TRANSITION_SEC)
        }

        bottomSheetShuffleButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
        }

        removeColorScheme()

        activity.controllerViewModel.addControllerCallback(activity.lifecycle) { _, _ ->
            firstTime = true
            instance?.addListener(this@FullBottomSheet)
            updateTimer()
            onRepeatModeChanged(instance?.repeatMode ?: Player.REPEAT_MODE_OFF)
            onShuffleModeEnabledChanged(instance?.shuffleModeEnabled == true)
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            firstTime = false
            /*
            if (activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition]
                    .songList.contains(instance.currentMediaItem)) {
                bottomSheetFavoriteButton.isChecked = true
                // TODO
            } else {
                bottomSheetFavoriteButton.isChecked = false
                // TODO
            }

             */
        }
    }

    private fun updateTimer() {
        val t = instance?.getTimer()
        bottomSheetTimerButton.isChecked = t != null
        TooltipCompat.setTooltipText(
            bottomSheetTimerButton,
            if (t != null) context.getString(
                R.string.timer_expiry,
                DateFormat.getTimeFormat(context).format(System.currentTimeMillis() + t)
            ) else context.getString(R.string.timer)
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "color_accuracy" || key == "content_based_color") {
            if (DynamicColors.isDynamicColorAvailable() &&
                prefs.getBooleanStrict("content_based_color", true)
            ) {
                addColorScheme()
            } else {
                removeColorScheme()
            }
        } else {
            refreshSettings(key)
        }
    }

    private fun refreshSettings(key: String?) {
        if (key == null || key == "default_progress_bar") {
            if (prefs.getBooleanStrict("default_progress_bar", false)) {
                bottomSheetFullSlider.visibility = VISIBLE
                bottomSheetFullSeekBar.visibility = GONE
            } else {
                bottomSheetFullSlider.visibility = GONE
                bottomSheetFullSeekBar.visibility = VISIBLE
            }
        }
        if (key == null || key == "centered_title") {
            if (prefs.getBooleanStrict("centered_title", true)) {
                bottomSheetFullTitle.gravity = Gravity.CENTER
                bottomSheetFullSubtitle.gravity = Gravity.CENTER
            } else {
                bottomSheetFullTitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
                bottomSheetFullSubtitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
            }
        }
        if (key == null || key == "bold_title") {
            if (prefs.getBooleanStrict("bold_title", true)) {
                bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 600, false)
            } else {
                bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 400, false)
            }
        }
        if (key == null || key == "album_round_corner") {
            bottomSheetFullCoverFrame.radius = prefs.getIntStrict(
                "album_round_corner",
                context.resources.getInteger(R.integer.round_corner_radius)
            ).dpToPx(context).toFloat()
        }
    }

    fun onStop() {
        runnableRunning = false
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
        ViewCompat.dispatchApplyWindowInsets(bottomSheetFullLyricView, insets.clone())
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .build()
            .toWindowInsets()!!
    }

    private fun removeColorScheme() {
        currentJob?.cancel()
        wrappedContext = null
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            applyColorScheme()
        }
    }

    private fun addColorScheme() {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            var drawable = bottomSheetFullCover.drawable
            if (drawable is TransitionDrawable) drawable = drawable.getDrawable(1)
            val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
                removeColorScheme()
                return@launch
            }
            val colorAccuracy = prefs.getBoolean("color_accuracy", false)
            val targetWidth = if (colorAccuracy) (bitmap.width / 4).coerceAtMost(256) else 16
            val targetHeight = if (colorAccuracy) (bitmap.height / 4).coerceAtMost(256) else 16
            val scaledBitmap =
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)

            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(scaledBitmap)
                .build() // <-- this is computationally expensive!

            wrappedContext = DynamicColors.wrapContextIfAvailable(
                context,
                options
            ).apply {
                // TODO does https://stackoverflow.com/a/58004553 describe this or another bug? will google ever fix anything?
                resources.configuration.uiMode = context.resources.configuration.uiMode
            }

            applyColorScheme()
        }
    }

    private fun updateQualityIndicators(info: AudioFormatInfo?) {
        (bottomSheetFullQualityDetails.getTag(R.id.fade_in_animation) as ViewPropertyAnimator?)?.cancel()
        (bottomSheetFullQualityDetails.getTag(R.id.fade_out_animation) as ViewPropertyAnimator?)?.cancel()
        if (info == null && bottomSheetFullQualityDetails.visibility == INVISIBLE) return
        val oldInfo = (bottomSheetFullQualityDetails.getTag(R.id.quality_details) as AudioFormatInfo?)
        if (oldInfo != null)
            applyQualityInfo(oldInfo)
        if (oldInfo != null || info != null)
            bottomSheetFullQualityDetails.setTag(R.id.quality_details, info)
        bottomSheetFullQualityDetails.fadOutAnimation(300) {
            bottomSheetFullQualityDetails.setTag(R.id.quality_details, null)
            if (info == null)
                return@fadOutAnimation
            applyQualityInfo(info)
            bottomSheetFullQualityDetails.fadInAnimation(300)
        }
    }

    private fun applyQualityInfo(info: AudioFormatInfo) {
        val icon = when (info.spatialFormat) {
            SpatialFormat.SURROUND_5_0,
            SpatialFormat.SURROUND_5_1,
            SpatialFormat.SURROUND_6_1,
            SpatialFormat.SURROUND_7_1 -> R.drawable.ic_surround_sound

            SpatialFormat.DOLBY_AC3,
            SpatialFormat.DOLBY_AC4,
            SpatialFormat.DOLBY_EAC3,
            SpatialFormat.DOLBY_EAC3_JOC -> R.drawable.ic_dolby

            // TODO dts icon

            else -> when (info.quality) {
                AudioQuality.HIRES -> R.drawable.ic_high_res
                AudioQuality.HD -> R.drawable.ic_hd
                AudioQuality.CD -> R.drawable.ic_cd
                AudioQuality.LOSSY -> R.drawable.ic_lossy
                else -> null
            }
        }

        val drawable = icon?.let { iconRes ->
            AppCompatResources.getDrawable(context, iconRes)?.apply {
                setBounds(0, 0, 18.dpToPx(context), 18.dpToPx(context))
            }
        }
        bottomSheetFullQualityDetails.setCompoundDrawablesRelative(drawable, null, null, null)

        val channelInfo = if (info.isDownMixing) {
            "${info.sourceChannels}ch ➝ ${info.deviceChannels}ch"
        } else {
            "${info.sourceChannels}ch"
        }

        bottomSheetFullQualityDetails.text = buildString {
            info.bitDepth?.let {
                append("${it}bit / ")
            }
            append("${info.sampleRate / 1000f}kHz")
            append(" / $channelInfo")
            info.bitrate?.let {
                append(" / ${it / 1000}kbps")
            }
        }
    }

    private suspend fun applyColorScheme() {
        val ctx = wrappedContext ?: context

        val colorSurface = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorSurface,
            -1
        )

        val colorOnSurface = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorOnSurface,
            -1
        )

        val colorOnSurfaceVariant = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            -1
        )

        val colorPrimary =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorPrimary,
                -1
            )

        val colorSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSecondaryContainer,
                -1
            )

        val colorOnSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                -1
            )

        val selectorBackground =
            AppCompatResources.getColorStateList(
                ctx,
                R.color.sl_check_button
            )

        val selectorFavBackground =
            AppCompatResources.getColorStateList(
                ctx,
                R.color.sl_fav_button
            )

        val colorAccent =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorAccent,
                -1
            )

        val backgroundProcessedColor = ColorUtils.getColor(
            colorSurface,
            ColorUtils.ColorType.COLOR_BACKGROUND_ELEVATED,
            ctx
        )

        val colorContrastFainted = ColorUtils.getColor(
            colorSecondaryContainer,
            ColorUtils.ColorType.COLOR_CONTRAST_FAINTED,
            ctx
        )

        val surfaceTransition = ValueAnimator.ofArgb(
            fullPlayerFinalColor,
            backgroundProcessedColor
        )

        val primaryTransition = ValueAnimator.ofArgb(
            colorPrimaryFinalColor,
            colorPrimary
        )

        val secondaryContainerTransition = ValueAnimator.ofArgb(
            colorSecondaryContainerFinalColor,
            colorSecondaryContainer
        )

        val onSecondaryContainerTransition = ValueAnimator.ofArgb(
            colorOnSecondaryContainerFinalColor,
            colorOnSecondaryContainer
        )

        val colorContrastFaintedTransition = ValueAnimator.ofArgb(
            colorContrastFaintedFinalColor,
            colorContrastFainted
        )

        surfaceTransition.apply {
            addUpdateListener { animation ->
                setBackgroundColor(
                    animation.animatedValue as Int
                )
                bottomSheetFullLyricView.setBackgroundColor(
                    animation.animatedValue as Int
                )
            }
            duration = BACKGROUND_COLOR_TRANSITION_SEC
        }

        primaryTransition.apply {
            addUpdateListener { animation ->
                val progressColor = animation.animatedValue as Int
                bottomSheetFullSlider.thumbTintList =
                    ColorStateList.valueOf(progressColor)
                bottomSheetFullSlider.trackActiveTintList =
                    ColorStateList.valueOf(progressColor)
                bottomSheetFullSeekBar.progressTintList =
                    ColorStateList.valueOf(progressColor)
                bottomSheetFullSeekBar.thumbTintList =
                    ColorStateList.valueOf(progressColor)
            }
            duration = BACKGROUND_COLOR_TRANSITION_SEC
        }

        secondaryContainerTransition.apply {
            addUpdateListener { animation ->
                val progressColor = animation.animatedValue as Int
                bottomSheetFullControllerButton.backgroundTintList =
                    ColorStateList.valueOf(progressColor)
            }
            duration = BACKGROUND_COLOR_TRANSITION_SEC
        }

        onSecondaryContainerTransition.apply {
            addUpdateListener { animation ->
                val progressColor = animation.animatedValue as Int
                bottomSheetFullControllerButton.iconTint =
                    ColorStateList.valueOf(progressColor)
            }
            duration = BACKGROUND_COLOR_TRANSITION_SEC
        }

        colorContrastFaintedTransition.apply {
            addUpdateListener { animation ->
                val progressColor = animation.animatedValue as Int
                bottomSheetFullSlider.trackInactiveTintList =
                    ColorStateList.valueOf(progressColor)
            }
        }

        withContext(Dispatchers.Main) {
            surfaceTransition.start()
            primaryTransition.start()
            secondaryContainerTransition.start()
            onSecondaryContainerTransition.start()
            colorContrastFaintedTransition.start()
        }

        delay(FOREGROUND_COLOR_TRANSITION_SEC)
        fullPlayerFinalColor = backgroundProcessedColor
        colorPrimaryFinalColor = colorPrimary
        colorSecondaryContainerFinalColor = colorSecondaryContainer
        colorOnSecondaryContainerFinalColor = colorOnSecondaryContainer
        colorContrastFaintedFinalColor = colorContrastFainted

        currentJob = null
        withContext(Dispatchers.Main) {
            bottomSheetFullTitle.setTextColor(
                colorOnSurface
            )
            bottomSheetFullSubtitle.setTextColor(
                colorOnSurfaceVariant
            )
            TextViewCompat.setCompoundDrawableTintList(
                bottomSheetFullQualityDetails,
                ColorStateList.valueOf(colorOnSurfaceVariant)
            )
            bottomSheetFullQualityDetails.setTextColor(
                colorOnSurfaceVariant
            )
            bottomSheetFullCoverFrame.setCardBackgroundColor(
                colorSurface
            )
            // TODO test/tweak walaoke colors
            bottomSheetFullLyricView.updateTextColor(
                androidx.core.graphics.ColorUtils.compositeColors(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 77),
                    fullPlayerFinalColor),
                colorPrimary,
                androidx.core.graphics.ColorUtils.compositeColors(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(Color.BLUE, 77),
                    fullPlayerFinalColor),
                Color.BLUE,
                androidx.core.graphics.ColorUtils.compositeColors(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(Color.RED, 77),
                    fullPlayerFinalColor),
                Color.RED,
                androidx.core.graphics.ColorUtils.compositeColors(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(Color.MAGENTA, 77),
                    fullPlayerFinalColor),
                Color.MAGENTA
            )

            bottomSheetTimerButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetPlaylistButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetShuffleButton.iconTint =
                selectorBackground
            bottomSheetLoopButton.iconTint =
                selectorBackground
            bottomSheetLyricButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFavoriteButton.iconTint =
                selectorFavBackground

            bottomSheetFullNextButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFullPreviousButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFullSlideUpButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)

            bottomSheetFullPosition.setTextColor(
                colorAccent
            )
            bottomSheetFullDuration.setTextColor(
                colorAccent
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        if (instance?.mediaItemCount != 0) {
            val req = { data: Any?, block: ImageRequest.Builder.() -> Unit ->
                lastDisposable?.dispose()
                lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
                    data(data)
                    scale(Scale.FILL)
                    block()
                    error(R.drawable.ic_default_cover)
                    allowHardware(false)
                }.build())
            }
            val load = { data: Any? ->
                req(data) {
                    target(onSuccess = {
                        bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
                    }, onError = {
                        bottomSheetFullCover.setImageDrawable(it?.asDrawable(context.resources))
                    }) // do not react to onStart() which sets placeholder
                    listener(onSuccess = { _, _ ->
                        if (DynamicColors.isDynamicColorAvailable() &&
                            prefs.getBooleanStrict("content_based_color", true)
                        ) {
                            addColorScheme()
                        }
                    }, onError = { _, _ ->
                        if (DynamicColors.isDynamicColorAvailable() &&
                            prefs.getBooleanStrict("content_based_color", true)
                        ) {
                            removeColorScheme()
                        }
                    })
                }
            }
            val file = mediaItem?.getFile()
            if (hasScopedStorageV1() && (!hasScopedStorageWithMediaTypes()
                        || context.hasImagePermission()) && file != null
            ) {
                req(Pair(file, Size(bottomSheetFullCover.width, bottomSheetFullCover.height))) {
                    target(onSuccess = {
                        bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
                        if (DynamicColors.isDynamicColorAvailable() &&
                            prefs.getBooleanStrict("content_based_color", true)
                        ) {
                            addColorScheme()
                        }
                    }, onError = {
                        load(mediaItem.mediaMetadata.artworkUri)
                    })
                }
            } else {
                load(mediaItem?.mediaMetadata?.artworkUri)
            }
            bottomSheetFullTitle.setTextAnimation(
                mediaItem?.mediaMetadata?.title,
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            bottomSheetFullDuration.text = mediaItem?.mediaMetadata?.durationMs
                ?.let { CalculationUtils.convertDurationToTimeStamp(it) }
            if (playlistNowPlaying != null) {
                playlistNowPlaying!!.text = mediaItem?.mediaMetadata?.title
                playlistNowPlayingCover!!.load(mediaItem?.mediaMetadata?.artworkUri) {
                    placeholderScaleToFit(R.drawable.ic_default_cover)
                    crossfade(true)
                    error(R.drawable.ic_default_cover)
                }
            }

            /*
            if (activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition]
                    .songList.contains(instance.currentMediaItem)
            ) {
                // TODO
            } else {
                // TODO
            }

             */
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
            playlistNowPlayingCover?.dispose()
        }
        val position = CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
        val duration = instance?.currentMediaItem?.mediaMetadata?.durationMs
        if (duration != null && !isUserTracking) {
            bottomSheetFullSeekBar.max = duration.toInt()
            bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
            bottomSheetFullSlider.valueTo = duration.toFloat().coerceAtLeast(1f)
            bottomSheetFullSlider.value =
                min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
            bottomSheetFullPosition.text = position
        }
        bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        positionRunnable.run()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        bottomSheetShuffleButton.isChecked = shuffleModeEnabled
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> {
                bottomSheetLoopButton.isChecked = true
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
            }

            Player.REPEAT_MODE_ONE -> {
                bottomSheetLoopButton.isChecked = true
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat_one)
            }

            Player.REPEAT_MODE_OFF -> {
                bottomSheetLoopButton.isChecked = false
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (instance?.isPlaying == true) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 1) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        wrappedContext ?: context,
                        R.drawable.play_anim
                    )
                bottomSheetFullControllerButton.background =
                    AppCompatResources.getDrawable(context, R.drawable.bg_play_anim)
                bottomSheetFullControllerButton.icon.startAnimation()
                bottomSheetFullControllerButton.background.startAnimation()
                bottomSheetFullControllerButton.setTag(R.id.play_next, 1)
            }
            if (!isUserTracking) {
                progressDrawable.animate = true
            }
            if (!runnableRunning) {
                runnableRunning = true
                handler.postDelayed(positionRunnable, SLIDER_UPDATE_INTERVAL)
            }
        } else if (playbackState != Player.STATE_BUFFERING) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 2) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        wrappedContext ?: context,
                        R.drawable.pause_anim
                    )
                bottomSheetFullControllerButton.background =
                    AppCompatResources.getDrawable(context, R.drawable.bg_pause_anim)
                bottomSheetFullControllerButton.icon.startAnimation()
                bottomSheetFullControllerButton.background.startAnimation()
                bottomSheetFullControllerButton.setTag(R.id.play_next, 2)
            }
            if (!isUserTracking) {
                progressDrawable.animate = false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        //android.util.Log.e("hi","$keyCode") TODO this method is no-op, but why?
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                instance?.playOrPause(); true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                instance?.seekToPrevious(); true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                instance?.seekToNext(); true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
        val items = LinkedList<MediaItem>()
        for (i in 0 until instance!!.mediaItemCount) {
            items.add(instance!!.getMediaItemAt(i))
        }
        val indexes = LinkedList<Int>()
        val s = instance!!.shuffleModeEnabled
        var i = instance!!.currentTimeline.getFirstWindowIndex(s)
        while (i != C.INDEX_UNSET) {
            indexes.add(i)
            i = instance!!.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
        }
        return Pair(indexes, items)
    }

    private inner class PlaylistCardAdapter(
        private val activity: MainActivity
    ) : MyRecyclerView.Adapter<PlaylistCardAdapter.ViewHolder>() {

        var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder =
            ViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.adapter_list_card_playlist, parent, false),
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = playlist.second[playlist.first[holder.bindingAdapterPosition]]
            holder.songName.text = item.mediaMetadata.title
            holder.songArtist.text = item.mediaMetadata.artist
            holder.indicator.text = item.mediaMetadata.durationMs?.convertDurationToTimeStamp()
            holder.songCover.load(item.mediaMetadata.artworkUri) {
                placeholderScaleToFit(R.drawable.ic_default_cover)
                crossfade(true)
                error(R.drawable.ic_default_cover)
            }
            holder.closeButton.setOnClickListener { v ->
                ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                val pos = holder.bindingAdapterPosition
                val idx = playlist.first.removeAt(pos)
                playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
                instance?.removeMediaItem(idx)
                playlist.second.removeAt(idx)
                notifyItemRemoved(pos)
            }
            holder.itemView.setOnClickListener {
                ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                instance?.seekToDefaultPosition(playlist.first[holder.absoluteAdapterPosition])
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.songCover.dispose()
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = if (playlist.first.size != playlist.second.size)
            throw IllegalStateException()
        else playlist.first.size

        inner class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val songName: TextView = view.findViewById(R.id.title)
            val songArtist: TextView = view.findViewById(R.id.artist)
            val songCover: ImageView = view.findViewById(R.id.cover)
            val indicator: TextView = view.findViewById(R.id.indicator)
            val closeButton: MaterialButton = view.findViewById(R.id.close)
        }

        fun onRowMoved(from: Int, to: Int) {
            val mediaController = activity.getPlayer()
            val from1 = playlist.first.removeAt(from)
            playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
            val movedItem = playlist.second.removeAt(from1)
            val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
            playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
            playlist.first.add(to, to1)
            playlist.second.add(to1, movedItem)
            mediaController?.moveMediaItem(from1, to1)
            notifyItemMoved(from, to)
        }
    }


    private class PlaylistCardMoveCallback(private val touchHelperContract: (Int, Int) -> Unit) :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled(): Boolean {
            return true
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return false
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            touchHelperContract(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            throw IllegalStateException()
        }
    }

    /*
    @Suppress("DEPRECATION")
    private fun insertIntoPlaylist(song: MediaItem) {
        val playlistEntry = ContentValues()
        val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id
        playlistEntry.put(MediaStore.Audio.Playlists.Members.PLAYLIST_ID, playlistId)
        playlistEntry.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)

        context.contentResolver.insert(
            MediaStore.Audio.Playlists.Members.getContentUri(
                "external",
                playlistId
            ), playlistEntry
        )
        activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.add(song)
    }

    @Suppress("DEPRECATION")
    private fun removeFromPlaylist(song: MediaItem) {
        val selection = "${MediaStore.Audio.Playlists.Members.AUDIO_ID} = ?"
        val selectionArgs = arrayOf(song.mediaId)
        val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id

        context.contentResolver.delete(
            MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
            selection,
            selectionArgs
        )
        activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.remove(song)
    }

     */

    private val positionRunnable = object : Runnable {
        override fun run() {
            val position =
                CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
            val duration = instance?.currentMediaItem?.mediaMetadata?.durationMs
            if (duration != null && !isUserTracking) {
                bottomSheetFullSeekBar.max = duration.toInt()
                bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
                bottomSheetFullSlider.valueTo = duration.toFloat()
                bottomSheetFullSlider.value =
                    min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
                bottomSheetFullPosition.text = position
            }
            bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
            if (instance?.isPlaying == true && runnableRunning) {
                handler.postDelayed(this, SLIDER_UPDATE_INTERVAL)
            } else {
                runnableRunning = false
            }
        }
    }

}
