package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.MediaControllerViewModel
import kotlin.math.max
import androidx.core.view.isVisible
import org.akanework.gramophone.logic.updateMargin

class LyricsView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
    SharedPreferences.OnSharedPreferenceChangeListener {


    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var recyclerView: MyRecyclerView? = null
    private var newView: NewLyricsView? = null
    private val adapter
        get() = recyclerView?.adapter as LegacyLyricsAdapter?
    var defaultTextColor = 0
        private set
    var highlightTextColor = 0
        private set
    var highlightTlTextColor = 0
        private set
    private var lyrics: SemanticLyrics? = null
    private val fullPlayer by lazy { (parent.parent as ViewGroup)
        .findViewById<FullBottomSheet>(R.id.full_player)!! }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val myInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
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
        createView()
    }

    private fun createView() {
        removeAllViews()
        val oldPaddingTop = newView?.paddingTop ?: recyclerView?.paddingTop ?: 0
        val oldPaddingBottom = newView?.paddingBottom ?: recyclerView?.paddingBottom ?: 0
        val oldPaddingLeft = newView?.paddingLeft ?: recyclerView?.paddingLeft ?: 0
        val oldPaddingRight = newView?.paddingRight ?: recyclerView?.paddingRight ?: 0
        adapter?.callback?.destroy()
        newView?.instance?.destroy()
        recyclerView = null
        newView = null
        val cb = object : NewLyricsView.Callbacks, Player.Listener, LifecycleOwner {
            private var waitingForSeek = 0
            private var waitingForSeekPos = 0uL
            override val lifecycle = LifecycleRegistry(this)

            init {
                lifecycle.currentState = Lifecycle.State.CREATED
                (context as MainActivity).controllerViewModel.addRecreationalPlayerListener(
                    lifecycle, this) {}
            }

            // TODO https://github.com/androidx/media/issues/1578
            override fun getCurrentPosition(): ULong =
                if (waitingForSeek > 0) waitingForSeekPos else
                GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                    ?.endedWorkaroundPlayer?.currentPosition?.toULong()
                    ?: (context as MainActivity).getPlayer()?.currentPosition?.toULong() ?: 0uL

            override fun isPlaying() = (context as MainActivity).getPlayer()?.isPlaying == true

            override fun seekTo(position: ULong) {
                // TODO: call handleSeek from onPositionDiscontinuity once there is a synchronized
                //  way of getting position (ie not relying on ExoPlayer and MediaController both
                //  anymore) - we can't call handleSeek a single frame too early or late from
                //  changing getCurrentPosition() or there are visible glitches
                newView?.handleSeek(getCurrentPosition(), position)
                waitingForSeek = max(0, waitingForSeek) + 1
                waitingForSeekPos = position
                (GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
                    ?: (context as MainActivity).getPlayer())?.seekTo(position.toLong())
            }

            override fun setPlayWhenReady(play: Boolean) {
                (context as MainActivity).getPlayer()?.playWhenReady = play
            }

            override fun speed(): Float {
                return (context as MainActivity).getPlayer()?.playbackParameters?.speed ?: 1f
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: @Player.DiscontinuityReason Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    waitingForSeek--
                }
            }

            override fun destroy() {
                lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }
        if (prefs.getBooleanStrict("lyric_ui_v2", true)) {
            inflate(context, R.layout.lyric_view_v2, this)
            newView = findViewById(R.id.lyric_view)!!
            newView!!.setPadding(oldPaddingLeft, oldPaddingTop, oldPaddingRight, oldPaddingBottom)
            newView!!.instance = cb
            newView!!.updateTextColor(
                defaultTextColor, highlightTextColor, highlightTlTextColor
            )
            newView!!.updateLyrics(lyrics)
        } else {
            inflate(context, R.layout.lyric_view, this)
            recyclerView = findViewById(R.id.recycler_view)
            recyclerView?.setPadding(
                oldPaddingLeft,
                oldPaddingTop,
                oldPaddingRight,
                oldPaddingBottom
            )
            recyclerView!!.adapter = LegacyLyricsAdapter(context).also {
                it.updateTextColor(defaultTextColor, highlightTextColor)
            }
            recyclerView!!.addItemDecoration(LyricPaddingDecoration(context))
            adapter!!.callback = cb
            adapter!!.updateLyrics(lyrics)
        }
    }

    private fun updateFullPlayerVisibility() {
        fullPlayer.visibilityDueToLyrics = if (isVisible && alpha == 1f && scaleX == 1f &&
            scaleY == 1f && translationX == 0f && translationY == 0f && !hasTransientState())
                INVISIBLE else VISIBLE
    }

    override fun setHasTransientState(hasTransientState: Boolean) {
        super.setHasTransientState(hasTransientState)
        updateFullPlayerVisibility()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        updateFullPlayerVisibility()
    }

    override fun setAlpha(alpha: Float) {
        super.setAlpha(alpha)
        updateFullPlayerVisibility()
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        updateFullPlayerVisibility()
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        updateFullPlayerVisibility()
    }

    override fun setTranslationX(translationX: Float) {
        super.setTranslationX(translationX)
        updateFullPlayerVisibility()
    }

    override fun setTranslationY(translationY: Float) {
        super.setTranslationY(translationY)
        updateFullPlayerVisibility()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        adapter?.updateLyricStatus()
        updateFullPlayerVisibility()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "lyric_center" || key == "lyric_bold")
            adapter?.onPrefsChanged()
        if (key == "lyric_center" || key == "lyric_bold" || key == "lyric_no_animation" ||
            key == "translation_auto_word" || key == "lyric_text_size"
        )
            newView?.onPrefsChanged(key)
        else if (key == "lyric_ui_v2")
            createView()
    }

    fun updateLyricPositionFromPlaybackPos() {
        adapter?.updateLyricPositionFromPlaybackPos()
        newView?.updateLyricPositionFromPlaybackPos()
    }

    fun updateLyrics(parsedLyrics: SemanticLyrics?) {
        lyrics = parsedLyrics
        adapter?.updateLyrics(lyrics)
        newView?.updateLyrics(lyrics)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        recyclerView?.setPadding(left, top, right, bottom)
        newView?.setPadding(left, top, right, bottom)
    }

    fun updateTextColor(
        newColor: Int, newHighlightColor: Int, newHighlightTlColor: Int
    ) {
        defaultTextColor = newColor
        highlightTextColor = newHighlightColor
        highlightTlTextColor = newHighlightTlColor
        adapter?.updateTextColor(defaultTextColor, highlightTextColor)
        newView?.updateTextColor(
            defaultTextColor, highlightTextColor, highlightTlTextColor
        )
    }

    fun updateTextColor(newColor: Int) {
        defaultTextColor = newColor
        adapter?.updateTextColor(defaultTextColor, highlightTextColor)
        newView?.updateTextColor(defaultTextColor)
    }

    fun updateHighlightColor(newHighlightColor: Int) {
        highlightTextColor = newHighlightColor
        adapter?.updateTextColor(defaultTextColor, highlightTextColor)
        newView?.updateHighlightColor(highlightTextColor)
    }

    fun updateHighlightTlColor(newHighlightTlColor: Int) {
        highlightTlTextColor = newHighlightTlColor
        newView?.updateHighlightTlColor(highlightTlTextColor)
    }
}
