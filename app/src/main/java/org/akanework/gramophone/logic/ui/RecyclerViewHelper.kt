/*
 *     Copyright (C) 2019 Google LLC
 *                   2024 Akane Foundation
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
package org.akanework.gramophone.logic.ui

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.fastscroll.Predicate
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.isEmpty

// Changes:
// - Kotlin
// - RecyclerView -> MyRecyclerView
// - use scrollToPositionWithOffsetCompat instead of layoutManager.scrollToPositionWithOffset
// - ItemHeightHelper support (supports variable item height, or default behaviour if null)
// - if ItemHeightHelper is set, it counts by adapter position to support flexible grid layouts
// - nice popup text interpolation (thanks stranger on github :D)
internal class RecyclerViewHelper(
    private val mView: MyRecyclerView,
    private val mPopupTextProvider: PopupTextProvider?,
    private val itemHeightHelper: ItemHeightHelper?
) : FastScroller.ViewHelper {
    private val mTempRect = Rect()
    override fun addOnPreDrawListener(onPreDraw: Runnable) {
        mView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(
                canvas: Canvas, parent: RecyclerView,
                state: RecyclerView.State
            ) {
                onPreDraw.run()
            }
        })
    }

    override fun addOnScrollChangedListener(onScrollChanged: Runnable) {
        mView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onScrollChanged.run()
            }
        })
    }

    override fun addOnTouchEventListener(onTouchEvent: Predicate<MotionEvent>) {
        mView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(
                recyclerView: RecyclerView,
                event: MotionEvent
            ): Boolean {
                return onTouchEvent.test(event)
            }

            override fun onTouchEvent(
                recyclerView: RecyclerView,
                event: MotionEvent
            ) {
                onTouchEvent.test(event)
            }
        })
    }

    override fun getScrollRange(): Int {
        val itemCount = itemCount
        if (itemCount == 0) {
            return 0
        }
        return mView.paddingTop + getItemHeightFromZeroTo(itemCount) + mView.paddingBottom
    }

    override fun getScrollOffset(): Int {
        val firstItemPosition = firstItemPosition
        if (firstItemPosition == RecyclerView.NO_POSITION) {
            return 0
        }
        val firstItemTop = firstItemOffset
        return mView.paddingTop + getItemHeightFromZeroTo(firstItemPosition) - firstItemTop
    }

    override fun scrollTo(offset: Int) {
        // Stop any scroll in progress for RecyclerView.
        var newOffset = offset
        mView.stopScroll()
        newOffset -= mView.paddingTop
        var firstItemPosition = 0
        if (itemHeightHelper != null) {
            var h = 0
            while (h < newOffset) {
                h = itemHeightHelper.getItemHeightFromZeroTo(++firstItemPosition)
            }
            firstItemPosition = (firstItemPosition - 1).coerceAtLeast(0)
        } else {
            // firstItemPosition should be non-negative even if paddingTop is greater than item height.
            firstItemPosition = max(0.0, (newOffset / itemHeight).toDouble()).toInt()
        }
        val firstItemTop = getItemHeightFromZeroTo(firstItemPosition) - newOffset
        scrollToPositionWithOffset(firstItemPosition, firstItemTop)
    }

    override fun getPopupText(): CharSequence? {
        var popupTextProvider = mPopupTextProvider
        if (popupTextProvider == null) {
            val adapter = mView.adapter
            if (adapter is PopupTextProvider) {
                popupTextProvider = adapter
            }
        }
        if (popupTextProvider == null) {
            return null
        }
        val position = getPopupTextPosition()
        return if (position == RecyclerView.NO_POSITION) {
            null
        } else popupTextProvider.getPopupText(mView, position)
    }

    private fun getItemHeightFromZeroTo(to: Int): Int {
        return itemHeightHelper?.getItemHeightFromZeroTo(to) ?: (itemHeight * to)
    }

    // https://github.com/zhanghai/AndroidFastScroll/issues/11#issuecomment-922413668
    private fun getPopupTextPosition(): Int {
        val position = firstItemAdapterPosition
        if (position == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION
        val linearLayoutManager = verticalLinearLayoutManager ?: return position
        val viewportHeight = mView.height
        val range = max((getScrollRange() - viewportHeight).toDouble(), 1.0).toInt()
        val offset = min(getScrollOffset().toDouble(), range.toDouble()).toInt()
        val firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = linearLayoutManager.findLastVisibleItemPosition()
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION
            || lastVisibleItemPosition == RecyclerView.NO_POSITION
        ) return position
        val positionOffset =
            ((lastVisibleItemPosition - firstVisibleItemPosition + 1) * 1.0 * offset / range).toInt()
        return (position + positionOffset).coerceAtMost(itemCount - 1)
    }

    private val itemCount: Int
        get() {
            val linearLayoutManager = verticalLinearLayoutManager ?: return 0
            var itemCount = linearLayoutManager.itemCount
            if (itemCount == 0) {
                return 0
            }
            if (itemHeightHelper == null && linearLayoutManager is GridLayoutManager) {
                itemCount = (itemCount - 1) / linearLayoutManager.spanCount + 1
            }
            return itemCount
        }
    private val itemHeight: Int
        get() {
            if (mView.isEmpty()) {
                return 0
            }
            val itemView = mView.getChildAt(0)
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect)
            return mTempRect.height()
        }
    private val firstItemPosition: Int
        get() {
            var position = firstItemAdapterPosition
            val linearLayoutManager = verticalLinearLayoutManager
                ?: return RecyclerView.NO_POSITION
            if (itemHeightHelper == null && linearLayoutManager is GridLayoutManager) {
                position /= linearLayoutManager.spanCount
            }
            return position
        }
    private val firstItemAdapterPosition: Int
        get() {
            if (mView.isEmpty()) {
                return RecyclerView.NO_POSITION
            }
            val itemView = mView.getChildAt(0)
            val linearLayoutManager = verticalLinearLayoutManager
                ?: return RecyclerView.NO_POSITION
            return linearLayoutManager.getPosition(itemView)
        }
    private val firstItemOffset: Int
        get() {
            if (mView.isEmpty()) {
                return RecyclerView.NO_POSITION
            }
            val itemView = mView.getChildAt(0)
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect)
            return mTempRect.top
        }

    private fun scrollToPositionWithOffset(position: Int, offset: Int) {
        var newPosition = position
        var newOffset = offset
        val linearLayoutManager = verticalLinearLayoutManager ?: return
        if (itemHeightHelper == null && linearLayoutManager is GridLayoutManager) {
            newPosition *= linearLayoutManager.spanCount
        }
        // LinearLayoutManager actually takes offset from paddingTop instead of top of RecyclerView.
        newOffset -= mView.paddingTop
        mView.scrollToPositionWithOffsetCompat(newPosition, newOffset)
    }

    private val verticalLinearLayoutManager: LinearLayoutManager?
        get() {
            val layoutManager = mView.layoutManager as? LinearLayoutManager ?: return null
            return if (layoutManager.orientation != RecyclerView.VERTICAL) {
                null
            } else layoutManager
        }
}

fun interface ItemHeightHelper {
    // get amount of pixels from element 0's top to element `to`s top
    fun getItemHeightFromZeroTo(to: Int): Int
}