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

package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.BlacklistFolderAdapter

class BlacklistHostFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.activity_blacklist_settings, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)
        val viewPager2 = rootView.findViewById<ViewPager2>(R.id.fragment_viewpager)

        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()

        topAppBar.setNavigationOnClickListener {
            (requireActivity() as MainActivity).navigateUp()
        }

        viewPager2.adapter = object : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {
            override fun createFragment(position: Int): Fragment {
                return BlacklistSettingsFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean("IsWhiteList", position == 1)
                    }
                }
            }

            override fun getItemCount(): Int {
                return 2
            }
        }

        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = getString(if (position == 1) R.string.settings_whitelist else
                R.string.settings_blacklist)
            tab.view.post {
                try {
                    val lp = tab.view.layoutParams as ViewGroup.MarginLayoutParams
                    lp.marginStart = if (position == 0)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    lp.marginEnd = if (position == tabLayout.tabCount - 1)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    tab.view.layoutParams = lp
                } catch (_: IllegalStateException) {
                }
            }
        }.attach()

        return rootView
    }
}

class BlacklistSettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val isWhitelist = requireArguments().getBoolean("IsWhiteList")
        val rootView = inflater.inflate(R.layout.fragment_blacklist_settings,
            container, false)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)

        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = BlacklistFolderAdapter(requireActivity() as AppCompatActivity, prefs,
            isWhitelist)
        recyclerView.adapter = if (isWhitelist) {
            ConcatAdapter(object : RecyclerView.Adapter<ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): ViewHolder = ViewHolder(
                    requireActivity().layoutInflater.inflate(
                        R.layout.fragment_whitelist_settings,
                        parent,
                        false
                    )
                )

                override fun onBindViewHolder(
                    holder: ViewHolder,
                    position: Int
                ) {
                    // do nothing
                }

                override fun getItemCount() = 1
            }, adapter)
        } else adapter

        return rootView
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}