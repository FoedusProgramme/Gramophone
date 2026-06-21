/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.logic.utils

import android.os.Build
import org.akanework.gramophone.BuildConfig

object Flags {
    const val TEST_RG_OFFLOAD = false // test only
    const val TTML_AGENT_SMART_SIDES = true
    const val HIDE_SAME_TRANSLATIONS = true
    const val IGNORE_SMALL_ENDTIME_GAPS = true
    const val NO_ANIM_GRADIENT_LAST_FRAME_MONKEY_FIX = true
    // The issue with this flag is pre-R which may have user-visible regressions when extra files
    // (covers or lyrics) are not indexed, that with this on now must be indexed.
    // The hopefully uncontroversial part (using MediaStore for songs) is always enabled.
    val MEDIASTORE_IO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    const val REMOVE_IMAGE_PERMISSION = BuildConfig.IS_GOOGLEPLAY

    // Before turning it on in prod we need i18n.
    const val FORMAT_INFO_DIALOG = true // TODO(ASAP)

    // Before turning offload to true in prod we'd need a conflict resolution UI in case DPE is not
    // offloadable and RG is turned on while user tries to turn on offload (and other way around).
    const val OFFLOAD = false

    // Multiple queues
    const val MQ_PREVIEW = true
}
