/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.Insets
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.preference.PreferenceManager
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.motion.MaterialBottomContainerBackHelper
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.logic.ui.MyBottomSheetBehavior
import org.akanework.gramophone.ui.MainActivity


class PlayerBottomSheet private constructor(
    context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes),
    Player.Listener, DefaultLifecycleObserver {
    constructor(context: Context, attributeSet: AttributeSet?)
            : this(context, attributeSet, 0, 0)

    companion object {
        private const val TAG = "PlayerBottomSheet"
    }

    private var lastDisposable: Disposable? = null
    private var standardBottomSheetBehavior: MyBottomSheetBehavior<FrameLayout>? = null
    @SuppressLint("RestrictedApi")
    private var lyricsBackHelper: MaterialBottomContainerBackHelper? = null
    private var bottomSheetBackCallback: OnBackPressedCallback? = null
    val fullPlayer: FullBottomSheet
    private val previewPlayer: View
    private val bottomSheetPreviewCover: ImageView
    private val bottomSheetPreviewTitle: TextView
    private val bottomSheetPreviewSubtitle: TextView
    private val bottomSheetPreviewControllerButton: MaterialButton
    private val bottomSheetPreviewNextButton: MaterialButton

    private val activity
        get() = context as MainActivity
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val lifecycleOwner: LifecycleOwner
        get() = activity
    private val handler = Handler(Looper.getMainLooper())
    private val instance: MediaController?
        get() = activity.getPlayer()
    private var lastActuallyVisible: Boolean? = null
    private var lastMeasuredHeight: Int? = null
    var visible = false
        set(value) {
            if (field != value) {
                field = value
                // TODO: this should be animated with predictive back once we switch to a better
                //  back stack.
                standardBottomSheetBehavior?.state =
                    if ((instance?.mediaItemCount ?: 0) > 0 && value) {
                        if (standardBottomSheetBehavior?.state
                            != BottomSheetBehavior.STATE_EXPANDED
                        )
                            BottomSheetBehavior.STATE_COLLAPSED
                        else BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_HIDDEN
                    }
            }
        }
    val actuallyVisible: Boolean
        get() = standardBottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN

    init {
        inflate(context, R.layout.bottom_sheet, this)
        previewPlayer = findViewById(R.id.preview_player)
        fullPlayer = findViewById(R.id.full_player)
        bottomSheetPreviewTitle = findViewById(R.id.preview_song_name)
        bottomSheetPreviewSubtitle = findViewById(R.id.preview_artist_name)
        bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
        bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)
        bottomSheetPreviewNextButton = findViewById(R.id.preview_next)

        setOnClickListener {
            if (standardBottomSheetBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
                standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        bottomSheetPreviewControllerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.playOrPause()
        }

        bottomSheetPreviewNextButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToNext()
        }

        activity.controllerViewModel.addControllerCallback(activity.lifecycle) { _, _ ->
            instance?.addListener(this@PlayerBottomSheet)
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            if ((activity.consumeAutoPlay() || prefs.getBooleanStrict(
                    "autoplay",
                    false
                )) && instance?.isPlaying != true
            ) {
                instance?.play()
            }
        }
    }

    private val bottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(
            bottomSheet: View,
            newState: Int,
        ) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    fullPlayer.visibility = GONE
                    previewPlayer.visibility = VISIBLE
                    previewPlayer.alpha = 1f
                    fullPlayer.alpha = 0f
                    bottomSheetBackCallback!!.isEnabled = false
                }

                BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                    fullPlayer.visibility = VISIBLE
                    previewPlayer.visibility = VISIBLE
                }

                BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    previewPlayer.visibility = GONE
                    fullPlayer.visibility = VISIBLE
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 1f
                    bottomSheetBackCallback!!.isEnabled = true
                }

                BottomSheetBehavior.STATE_HIDDEN -> {
                    previewPlayer.visibility = GONE
                    fullPlayer.visibility = GONE
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 0f
                    bottomSheetBackCallback!!.isEnabled = false
                }
            }
            dispatchBottomSheetInsets()
        }

        override fun onSlide(
            bottomSheet: View,
            slideOffset: Float,
        ) {
            if (slideOffset < 0) {
                // hidden state
                previewPlayer.alpha = 1 - (-1 * slideOffset)
                fullPlayer.alpha = 0f
                return
            }
            previewPlayer.alpha = 1 - (slideOffset)
            fullPlayer.alpha = slideOffset
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout { // wait for CoordinatorLayout to finish to allow getting behaviour
            standardBottomSheetBehavior = MyBottomSheetBehavior.from(this)
            fullPlayer.minimize = {
                standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            @SuppressLint("RestrictedApi")
            lyricsBackHelper = MaterialBottomContainerBackHelper(fullPlayer.bottomSheetFullLyricView)
            bottomSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.startBackProgress(backEvent)
                    } else {
                        standardBottomSheetBehavior!!.startBackProgress(backEvent)
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.updateBackProgress(backEvent)
                    } else {
                        standardBottomSheetBehavior!!.updateBackProgress(backEvent)
                    }
                }

                override fun handleOnBackPressed() {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        val backEvent = lyricsBackHelper!!.onHandleBackInvoked()
                        if (backEvent == null || backEvent.progress == 0f
                            || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            fullPlayer.bottomSheetFullLyricView.fadOutAnimation(FullBottomSheet.LYRIC_FADE_TRANSITION_SEC)
                            return
                        }
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.finishBackProgressNotPersistent(backEvent, object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                fullPlayer.bottomSheetFullLyricView.visibility = View.INVISIBLE
                            }
                        })
                    } else {
                        standardBottomSheetBehavior!!.handleBackInvoked()
                    }
                }

                override fun handleOnBackCancelled() {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.cancelBackProgress()
                    } else {
                        standardBottomSheetBehavior!!.cancelBackProgress()
                    }
                }
            }
            /*
            lyricSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackPressed() {
                    bottomSheetFullLyricRecyclerView.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetFullLyricGradientViewUp.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetFullLyricGradientViewDown.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetLyricButton.isChecked = false
                    activity.onBackPressedDispatcher.addCallback(activity, bottomSheetBackCallback!!)
                    bottomSheetBackCallback!!.isEnabled = true
                }
            }

             */
            activity.onBackPressedDispatcher.addCallback(activity, bottomSheetBackCallback!!)
            standardBottomSheetBehavior!!.addBottomSheetCallback(bottomSheetCallback)
            // this is required after onRestoreSavedInstanceState() in BottomSheetBehaviour
            bottomSheetCallback.onStateChanged(this, standardBottomSheetBehavior!!.state)
            lifecycleOwner.lifecycle.addObserver(this)
            updatePeekHeight()
            dispatchBottomSheetInsets()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastActuallyVisible = null
        lastMeasuredHeight = null
        fullPlayer.minimize = null
        lifecycleOwner.lifecycle.removeObserver(this)
        standardBottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBackCallback?.remove()
        standardBottomSheetBehavior = null
        onStop(lifecycleOwner)
    }

    private fun updatePeekHeight() {
        previewPlayer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.UNSPECIFIED
        )
        standardBottomSheetBehavior?.setPeekHeight(previewPlayer.measuredHeight, false)
    }

    fun getBottomPadding() = if (lastActuallyVisible == true) lastMeasuredHeight ?: 0 else 0

    private fun dispatchBottomSheetInsets() {
        if (lastMeasuredHeight == previewPlayer.measuredHeight &&
            lastActuallyVisible == actuallyVisible
        ) return
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatching bottom sheet insets")
        lastMeasuredHeight = previewPlayer.measuredHeight
        lastActuallyVisible = actuallyVisible
        // This dispatches the last known insets again to force regeneration of
        // FragmentContainerView's insets which will in turn call generateBottomSheetInsets().
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
        // We here have to set up inset padding manually as the bottom sheet won't know what
        // View is behind the status bar, paddingTopSystemWindowInsets just allows it to go
        // behind it, which differs from the other padding*SystemWindowInsets. We can't use the
        // other padding*SystemWindowInsets to apply systemBars() because previewPlayer and
        // fullPlayer should extend into system bars AND display cutout. previewPlayer can't use
        // fitsSystemWindows because it doesn't want top padding from status bar.
        // We have to do it manually, duh.
        previewPlayer.setPadding(myInsets.left, 0, myInsets.right, myInsets.bottom)
        // Let fullPlayer handle insets itself (and discard result as it's irrelevant to hierarchy)
        ViewCompat.dispatchApplyWindowInsets(fullPlayer, insets.clone())
        // Now make sure BottomSheetBehaviour has the correct View height set.
        if (isLaidOut && !isLayoutRequested) {
            updatePeekHeight()
        } else {
            doOnNextLayout {
                updatePeekHeight()
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
        if ((instance?.mediaItemCount ?: 0) > 0) {
            lastDisposable?.dispose()
            lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
                target(onSuccess = {
                    bottomSheetPreviewCover.setImageDrawable(it.asDrawable(context.resources))
                }, onError = {
                    bottomSheetPreviewCover.setImageDrawable(it?.asDrawable(context.resources))
                }) // do not react to onStart() which sets placeholder
                data(mediaItem?.mediaMetadata?.artworkUri)
                scale(Scale.FILL)
                error(R.drawable.ic_default_cover)
            }.build())
            bottomSheetPreviewTitle.text = mediaItem?.mediaMetadata?.title
            bottomSheetPreviewSubtitle.text =
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
        }
        var newState = standardBottomSheetBehavior?.state
        if ((instance?.mediaItemCount ?: 0) > 0 && visible) {
            if (newState != BottomSheetBehavior.STATE_EXPANDED) {
                newState = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else {
            newState = BottomSheetBehavior.STATE_HIDDEN
        }
        handler.post {
            // if we are destroyed after onMediaItemTransition but before this runs,
            // standardBottomSheetBehavior will be null
            standardBottomSheetBehavior?.state = newState
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) return
        val myTag = bottomSheetPreviewControllerButton.getTag(R.id.play_next) as Int?
        if (instance?.isPlaying == true && myTag != 1) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.play_anim)
            bottomSheetPreviewControllerButton.icon.startAnimation()
            bottomSheetPreviewControllerButton.setTag(R.id.play_next, 1)
        } else if (instance?.isPlaying == false && myTag != 2) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.pause_anim)
            bottomSheetPreviewControllerButton.icon.startAnimation()
            bottomSheetPreviewControllerButton.setTag(R.id.play_next, 2)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        fullPlayer.onStop()
    }

}