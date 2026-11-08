package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R

/**
 * ItemDecoration providing top/bottom padding for the first and last lyric items.
 *
 * @author SteveZMTstudios
 */
class LyricPaddingDecoration(
    context: Context,
    private val isCompactModeProvider: () -> Boolean
) : RecyclerView.ItemDecoration() {
    private val baseTopPadding = context.resources.getDimensionPixelSize(R.dimen.lyric_top_padding)
    private val baseBottomPadding = context.resources.getDimensionPixelSize(R.dimen.lyric_bottom_padding)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val itemPosition = parent.getChildAdapterPosition(view)
        val scale = if (isCompactModeProvider()) CompactLyricMetrics.DECORATION_PADDING_SCALE else 1.0f
        if (itemPosition == 0) {
            outRect.top = (baseTopPadding * scale).toInt()
        } else if (itemPosition == parent.adapter!!.itemCount - 1) {
            outRect.bottom = (baseBottomPadding * scale).toInt()
        }
    }
}