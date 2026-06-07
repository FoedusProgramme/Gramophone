/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import org.akanework.gramophone.ui.MainActivity

/**
 * BaseFragment:
 *   It is a base fragment for all main fragments that
 * can appear in MainActivity's FragmentContainer.
 *   It creates material transitions easily and also make
 * overlapping colors more convenient. It can also manage
 * whether to show up bottom's mini player or not.
 *
 * @author AkaneTan, nift4
 * @see MainActivity
 */
abstract class BaseFragment(val wantsPlayer: Boolean? = null) : Fragment() {

    protected val mainActivity
        get() = requireActivity() as MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable material transitions.
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
    }

    // https://github.com/material-components/material-components-android/issues/1984#issuecomment-1089710991
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Overlap colors.
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) return
        // see registerFragmentLifecycleCallbacks in MainActivity
        if (wantsPlayer != null) {
            (activity as MainActivity?)?.playerBottomSheet?.visible = wantsPlayer
        }
    }
}