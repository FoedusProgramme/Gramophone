package org.akanework.gramophone.logic.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class QueueRecyclerView : MyRecyclerView {
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int)
            : super(context, attributeSet, defStyleAttr)

    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet, 0)

    constructor(context: Context) : super(context, null)

    private var lastYPos = 0f

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // tell the parents to piss off *unless* we are at the top of the list AND this is a scroll
        // up gesture (a.k.a user wants to dismiss the bottom sheet)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastYPos = e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(canScrollVertically(-1) || e.y < lastYPos)
                lastYPos = e.y
            }
        }
        return super.onInterceptTouchEvent(e)
    }
}