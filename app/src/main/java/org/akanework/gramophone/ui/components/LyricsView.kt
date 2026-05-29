package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.MainActivity

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

    init {
        createView()
    }

    private fun createView() {
        removeAllViews()
        val oldPaddingTop = newView?.paddingTop ?: recyclerView?.paddingTop ?: 0
        val oldPaddingBottom = newView?.paddingBottom ?: recyclerView?.paddingBottom ?: 0
        val oldPaddingLeft = newView?.paddingLeft ?: recyclerView?.paddingLeft ?: 0
        val oldPaddingRight = newView?.paddingRight ?: recyclerView?.paddingRight ?: 0
        recyclerView = null
        newView = null
        val cb = object : NewLyricsView.Callbacks {
            // TODO https://github.com/androidx/media/issues/1578
            override fun getCurrentPosition(): ULong =
                GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                    ?.endedWorkaroundPlayer?.currentPosition?.toULong()
                    ?: (context as MainActivity).getPlayer()?.currentPosition?.toULong() ?: 0uL

            override fun seekTo(position: ULong) {
                (context as MainActivity).getPlayer()?.seekTo(position.toLong())
            }

            override fun setPlayWhenReady(play: Boolean) {
                (context as MainActivity).getPlayer()?.playWhenReady = play
            }

            override fun speed(): Float {
                return (context as MainActivity).getPlayer()?.playbackParameters?.speed ?: 1f
            }
        }
        if (prefs.getBooleanStrict("lyric_ui", false)) {
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        adapter?.updateLyricStatus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "lyric_center" || key == "lyric_bold")
            adapter?.onPrefsChanged()
        if (key == "lyric_center" || key == "lyric_bold" || key == "lyric_no_animation" ||
            key == "translation_auto_word"
        )
            newView?.onPrefsChanged(key)
        else if (key == "lyric_ui")
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
