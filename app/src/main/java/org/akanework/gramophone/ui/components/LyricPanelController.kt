/*
 *     Copyright (C) 2026 SteveZMTstudios
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

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.utils.SemanticLyrics

/**
 * Controller managing fullscreen and inline lyric presentation modes.
 *
 * ### Architectural Rationale for Dual [LyricsView] Instances
 * - ConstraintSet limitations: [androidx.constraintlayout.widget.ConstraintSet] only modifies
 *   constraints within the same ViewGroup; reparenting across ViewGroups requires `removeView` + `addView`,
 *   which resets layout and scroll states and triggers transient UI glitches.
 * - View visibility propagation: When fullscreen lyrics is active, [FullBottomSheet] itself is set
 *   to [View.GONE]. Because [inlineLyricView] is a descendant of [FullBottomSheet], a single view
 *   inside the sheet would be hidden by its ancestor in fullscreen mode.
 * - Background asymmetry: [inlineLyricView] requires a transparent background to show the dimmed
 *   artwork underneath, whereas [fullLyricView] has a dynamically painted solid theme background.
 *
 * Dismissal of fullscreen lyrics via system predictive back gesture is driven by [PlayerBottomSheet],
 * while this controller owns the lyrics button state, theme colors, position synchronization, and display toggling.
 *
 * @author SteveZMTstudios
 */
class LyricPanelController(
    private val fullLyricViewProvider: () -> LyricsView,
    private val inlineLyricFrame: FrameLayout,
    private val inlineLyricView: LyricsView,
    private val coverFrame: MaterialCardView,
    private val lyricButton: MaterialButton,
    private val prefs: SharedPreferences,
    private val contextProvider: () -> Context,
    private val onFullPlayerVisibilityRequest: (Int) -> Unit
) {
    private val fullLyricView: LyricsView
        get() = fullLyricViewProvider()

    val defaultTextColor: Int
        get() = fullLyricView.defaultTextColor

    val highlightTlTextColor: Int
        get() = fullLyricView.highlightTlTextColor

    companion object {
        const val INLINE_LYRIC_COVER_ALPHA: Float = 0.08f
        const val INLINE_LYRIC_FADE_DURATION: Long = 250L
    }

    private var isFullscreen: Boolean = prefs.getBooleanStrict("lyric_fullscreen", true)
    private var inlineVisible: Boolean = false

    init {
        inlineLyricView.isCompactMode = true
        // Note: Do NOT access fullLyricView in constructor, as it is resolved via parent ViewGroup
        // which is null during host view construction.
        updateButtonTint()
        updateButtonState()
    }

    /**
     * Must be called from the host view's onAttachedToWindow() before LyricsView attaches,
     * ensuring that the parent ViewGroup is available and the callback is registered before
     * the initial visibility dispatch.
     */
    fun attachFullLyricView() {
        fullLyricView.onFullPlayerVisibilityRequest = { visibility ->
            onFullPlayerVisibilityRequest(visibility)
            updateButtonState()
        }
    }

    fun updateLyrics(lyrics: SemanticLyrics?) {
        fullLyricView.updateLyrics(lyrics)
        inlineLyricView.updateLyrics(lyrics)
    }

    fun syncPlaybackPosition() {
        // Fullscreen lyric visibility is driven directly by view fade animations,
        // so fullLyricView.isVisible is the authoritative state for full player lyrics.
        if (fullLyricView.isVisible) {
            fullLyricView.updateLyricPositionFromPlaybackPos()
        }
        if (inlineVisible) {
            inlineLyricView.updateLyricPositionFromPlaybackPos()
        }
    }

    fun onLyricButtonClicked() {
        if (isFullscreen) {
            fullLyricView.fadInAnimation(FullBottomSheet.LYRIC_FADE_TRANSITION_SEC)
        } else {
            if (inlineVisible) {
                closeInlineLyrics()
            } else {
                openInlineLyrics()
            }
        }
    }

    private fun openInlineLyrics() {
        inlineVisible = true
        inlineLyricView.updateLyricPositionFromPlaybackPos()
        coverFrame.animate()
            .alpha(INLINE_LYRIC_COVER_ALPHA)
            .setDuration(INLINE_LYRIC_FADE_DURATION)
            .start()
        inlineLyricFrame.fadInAnimation(INLINE_LYRIC_FADE_DURATION)
        updateButtonState()
    }

    private fun closeInlineLyrics() {
        inlineVisible = false
        inlineLyricFrame.fadOutAnimation(INLINE_LYRIC_FADE_DURATION)
        coverFrame.animate()
            .alpha(1f)
            .setDuration(INLINE_LYRIC_FADE_DURATION)
            .start()
        updateButtonState()
    }

    private fun createLyricButtonColorStateList(
        colorOnSurface: Int,
        colorOnSurfaceVariant: Int
    ): ColorStateList {
        return if (isFullscreen) {
            ColorStateList.valueOf(colorOnSurface)
        } else {
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(colorOnSurface, colorOnSurfaceVariant)
            )
        }
    }

    fun updateButtonTint() {
        val ctx = contextProvider()
        val colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, -1)
        val colorInactive = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, -1)
        lyricButton.iconTint = createLyricButtonColorStateList(colorOnSurface, colorInactive)
    }

    fun createLyricButtonTransition(
        targetColorOnSurface: Int,
        targetColorOnSurfaceVariant: Int,
        duration: Long
    ): ValueAnimator {
        val lyricSelector = createLyricButtonColorStateList(targetColorOnSurface, targetColorOnSurfaceVariant)
        val startColor = lyricButton.iconTint?.getColorForState(
            lyricButton.drawableState, Color.RED
        ) ?: Color.RED
        val endColor = lyricSelector.getColorForState(
            lyricButton.drawableState, Color.RED
        )

        return ValueAnimator.ofArgb(startColor, endColor).apply {
            addUpdateListener { animation ->
                val progressColor = animation.animatedValue as Int
                lyricButton.iconTint = ColorStateList.valueOf(progressColor)
            }
            this.duration = duration
        }
    }

    private var currentIconRes: Int = 0

    private fun updateButtonState() {
        val inlineShown = !isFullscreen && inlineVisible
        lyricButton.isChecked = inlineShown
        val targetIcon = if (isFullscreen || inlineShown) R.drawable.ic_article
        else R.drawable.ic_art_track
        if (currentIconRes != targetIcon) {
            currentIconRes = targetIcon
            lyricButton.setIconResource(targetIcon)
        }
    }

    fun updateColors(lyricsTextColor: Int, colorPrimary: Int, lyricsHighlightTlColor: Int) {
        fullLyricView.updateTextColor(lyricsTextColor, colorPrimary, lyricsHighlightTlColor)
        inlineLyricView.updateTextColor(lyricsTextColor, colorPrimary, lyricsHighlightTlColor)
    }

    fun updateTextColor(textColor: Int) {
        fullLyricView.updateTextColor(textColor)
        inlineLyricView.updateTextColor(textColor)
    }

    fun updateHighlightColor(highlightColor: Int) {
        fullLyricView.updateHighlightColor(highlightColor)
        inlineLyricView.updateHighlightColor(highlightColor)
    }

    fun updateHighlightTlColor(highlightTlColor: Int) {
        fullLyricView.updateHighlightTlColor(highlightTlColor)
        inlineLyricView.updateHighlightTlColor(highlightTlColor)
    }

    fun setFullLyricBackgroundColor(color: Int) {
        fullLyricView.setBackgroundColor(color)
    }

    fun onPreferenceChanged(key: String?) {
        if (key == null || key == "lyric_fullscreen") {
            isFullscreen = prefs.getBooleanStrict("lyric_fullscreen", true)
            if (isFullscreen && inlineVisible) {
                inlineVisible = false
                inlineLyricFrame.visibility = View.GONE
                coverFrame.visibility = View.VISIBLE
                coverFrame.alpha = 1f
            }
            updateButtonTint()
            updateButtonState()
        }
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putBoolean("Lyrics", fullLyricView.isVisible)
        outState.putBoolean("InlineLyrics", inlineVisible)
    }

    fun restoreInstanceState(savedState: Bundle?) {
        if (savedState == null) return
        fullLyricView.isVisible = savedState.getBoolean("Lyrics", false)
        val savedInline = savedState.getBoolean("InlineLyrics", false)
        if (savedInline && !isFullscreen) {
            inlineVisible = true
            coverFrame.alpha = INLINE_LYRIC_COVER_ALPHA
            inlineLyricFrame.visibility = View.VISIBLE
            inlineLyricFrame.alpha = 1f
            inlineLyricView.updateLyricPositionFromPlaybackPos()
        } else {
            inlineVisible = false
        }
        updateButtonState()
    }
}
