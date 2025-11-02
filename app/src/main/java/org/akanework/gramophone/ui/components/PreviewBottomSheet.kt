package org.akanework.gramophone.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.viewpager2.widget.ViewPager2
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.ui.MainActivity

class PreviewBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener {
    private val activity
        get() = context as MainActivity
    private val instance: MediaController?
        get() = activity.getPlayer()
    private val bottomSheetPreviewCover: ImageView
    private val bottomSheetPreviewControllerButton: MaterialButton
    private val bottomSheetPreviewNextButton: MaterialButton
    private val pager: ViewPager2
    private var pagerAllowLeftSwipe = true
    private var pagerAllowRightSwipe = true
    private val adapter: PreviewPlayerPagerAdapter
    private var titles = listOf("", "", "")
    private var artists = listOf("", "", "")
    private var lastDisposable: Disposable? = null
    private var userScrollInProgress = false

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        inflate(context, R.layout.preview_player, this)
        bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
        bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)
        bottomSheetPreviewNextButton = findViewById(R.id.preview_next)
        bottomSheetPreviewControllerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.playOrPause()
        }

        bottomSheetPreviewNextButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToNext()
        }

        pager = findViewById(R.id.preview_player_pager)
        adapter = PreviewPlayerPagerAdapter(context)
        pager.adapter = adapter
        updatePages()
        pager.setCurrentItem(1, false)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> userScrollInProgress = true
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        if (userScrollInProgress) {
                            userScrollInProgress = false
                            handlePageSettled()
                        }
                    }
                }
            }
        })

        pager.getChildAt(0).setOnTouchListener(object: View.OnTouchListener {
            var initX = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> initX = event.x
                    MotionEvent.ACTION_MOVE -> {
                        val diff = event.x - initX
                        if (diff > 0 && !pagerAllowLeftSwipe) return true
                        if (diff < 0 && !pagerAllowRightSwipe) return true
                    }
                }
                return false
            }
        })

        activity.controllerViewModel.addControllerCallback(activity.lifecycle) { _, _ ->
            instance?.addListener(this@PreviewBottomSheet)
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
        }
    }

    private fun handlePageSettled() {
        when (pager.currentItem) {
            0 -> {
                if (instance?.hasPreviousMediaItem() == true){
                    ViewCompat.performHapticFeedback(activity.playerBottomSheet.previewPlayer, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                    instance?.seekToPrevious()
                }
            }
            2 -> {
                if (instance?.hasNextMediaItem() == true){
                    ViewCompat.performHapticFeedback(activity.playerBottomSheet.previewPlayer, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                    instance?.seekToNext()
                }
            }
            else -> return
        }
        pager.setCurrentItem(1, false)
        updatePages()
    }

    private fun updatePages() {
        adapter.updateData(
            titles,
            artists
        )

        pagerAllowRightSwipe = (instance?.hasNextMediaItem() == true)
        pagerAllowLeftSwipe = (instance?.hasPreviousMediaItem() == true)
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

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
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
                allowHardware(bottomSheetPreviewCover.isHardwareAccelerated)
                error(R.drawable.ic_default_cover)
            }.build())
            val prevIndex = (instance?.currentMediaItemIndex?.minus(1) ?: 0).coerceIn(0, instance?.mediaItemCount)
            val nextIndex = (instance?.currentMediaItemIndex?.plus(1) ?: 0).coerceIn(0, instance?.mediaItemCount)
            titles = listOf(
                instance?.getMediaItemAt(prevIndex)?.mediaMetadata?.title?.toString() ?: context.getString(R.string.unknown_title),
                mediaItem?.mediaMetadata?.title?.toString() ?: context.getString(R.string.unknown_title),
                instance?.getMediaItemAt(nextIndex)?.mediaMetadata?.title?.toString() ?: context.getString(R.string.unknown_title),
            )
            artists = listOf(
                instance?.getMediaItemAt(prevIndex)?.mediaMetadata?.artist?.toString() ?: context.getString(R.string.unknown_artist),
                mediaItem?.mediaMetadata?.artist?.toString() ?: context.getString(R.string.unknown_artist),
                instance?.getMediaItemAt(nextIndex)?.mediaMetadata?.artist?.toString() ?: context.getString(R.string.unknown_artist),
            )
            updatePages()
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
        }
    }
}