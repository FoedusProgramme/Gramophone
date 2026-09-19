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

package org.akanework.gramophone

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.akanework.gramophone.ui.widget.CardLayoutVariant
import org.akanework.gramophone.ui.widget.CircleLayoutVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WidgetLayoutProbeTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun testPillLayoutStandardVariantMeasuredSize() {
        val density = context.resources.displayMetrics.density
        val expectedWidthPx = (CircleLayoutVariant.PILL.minWidth * density).toInt()
        val expectedHeightPx = (CircleLayoutVariant.PILL.minHeight * density).toInt()

        // Real-device slots where PILL variant is selected (e.g. 2x1 slot 138x94, 2x2 122x122, min 110x56)
        val pillSlots = listOf(138 to 94, 122 to 122, 110 to 56)
        for ((wDp, hDp) in pillSlots) {
            val root = inflateAndMeasure(R.layout.card_widget_pill, wDp, hDp)
            val bg = root.findViewById<View>(R.id.widget_card_bg)
            assertNotNull("Pill background view must exist", bg)

            // Assert exact measured dimension matches CircleLayoutVariant.PILL specification (110dp x 56dp)
            assertEquals("Pill background width at ${wDp}x$hDp must match PILL variant spec", expectedWidthPx, bg.measuredWidth)
            assertEquals("Pill background height at ${wDp}x$hDp must match PILL variant spec", expectedHeightPx, bg.measuredHeight)
        }
    }

    @Test
    fun testPillLayoutSingleVariantMeasuredSize() {
        val density = context.resources.displayMetrics.density
        val expectedWidthPx = (CircleLayoutVariant.PILL_SINGLE.minWidth * density).toInt()
        val expectedHeightPx = (CircleLayoutVariant.PILL_SINGLE.minHeight * density).toInt()

        // 1x1 slot (56x56) where PILL_SINGLE is selected: cover is hidden (View.GONE)
        val root = LayoutInflater.from(context).inflate(R.layout.card_widget_pill, null) as ViewGroup
        val cover = root.findViewById<View>(R.id.widget_cover)
        cover.visibility = View.GONE

        val wPx = (56 * density).toInt()
        val hPx = (56 * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val bg = root.findViewById<View>(R.id.widget_card_bg)
        assertNotNull("Pill background view must exist", bg)
        // Assert exact measured dimension matches CircleLayoutVariant.PILL_SINGLE specification (56dp x 56dp)
        assertEquals("Pill single background width must match PILL_SINGLE variant spec", expectedWidthPx, bg.measuredWidth)
        assertEquals("Pill single background height must match PILL_SINGLE variant spec", expectedHeightPx, bg.measuredHeight)
    }

    @Test
    fun testCardLayoutBackgroundMeasuredSize() {
        val slots = listOf(191 to 88, 244 to 142, 180 to 50)
        for ((wDp, hDp) in slots) {
            val root = inflateAndMeasure(R.layout.card_widget, wDp, hDp)
            val bg = root.findViewById<View>(R.id.widget_card_bg)
            val d = context.resources.displayMetrics.density
            val expectedW = (wDp * d).toInt()
            val expectedH = (hDp * d).toInt()
            assertEquals("Card background width must fill parent bounds exactly", expectedW, bg.measuredWidth)
            assertEquals("Card background height must fill parent bounds exactly", expectedH, bg.measuredHeight)
        }
    }

    private fun inflateAndMeasure(layoutResId: Int, wDp: Int, hDp: Int): ViewGroup {
        val d = context.resources.displayMetrics.density
        val root = LayoutInflater.from(context).inflate(layoutResId, null) as ViewGroup
        root.measure(
            View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }
}
