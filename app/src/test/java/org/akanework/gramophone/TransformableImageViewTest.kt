/*
 *     Copyright (C) 2026 The Gramophone authors
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

import android.app.Application
import android.os.Looper
import android.view.View
import org.akanework.gramophone.ui.components.TransformableImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TransformableImageViewTest {

    @Test
    fun rotationStopsWhileWindowIsHidden() {
        val view = TransformableImageView(RuntimeEnvironment.getApplication())
        val looper = shadowOf(Looper.getMainLooper())

        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        view.startRotation()
        assertNotEquals(Duration.ZERO, looper.nextScheduledTaskTime)

        view.dispatchWindowVisibilityChanged(View.GONE)
        looper.runOneTask()
        assertEquals(Duration.ZERO, looper.nextScheduledTaskTime)
    }
}