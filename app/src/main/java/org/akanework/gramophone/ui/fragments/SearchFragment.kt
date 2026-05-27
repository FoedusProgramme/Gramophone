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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter

/**
 * SearchFragment:
 *   A fragment that contains a search bar which browses
 * the library finding items matching user input.
 *
 * @author AkaneTan
 */
class SearchFragment : BaseFragment(true) {
    // TODO this class leaks InsetSourceControl
    private lateinit var editText: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        editText = rootView.findViewById(R.id.edit_text)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val searchTextFlow = MutableStateFlow(arguments?.getString("query", "") ?: "")
        val songAdapter =
            SongAdapter(
                this, mainActivity.reader.songListFlow.combine(searchTextFlow.map {
                    it.trim()
                }) { list, text ->
                    list.filter {
                        // TODO sort results by match quality? (using raw=natural order)
                        val isMatchingTitle =
                            it.mediaMetadata.title?.contains(text, true) == true
                        val isMatchingAlbum =
                            it.mediaMetadata.albumTitle?.contains(text, true) == true
                        val isMatchingArtist =
                            it.mediaMetadata.artist?.contains(text, true) == true
                        isMatchingTitle || isMatchingAlbum || isMatchingArtist
                    }
                },
                isSubFragment = R.id.search, allowDiffUtils = true,
                rawOrderExposed = Sorter.Type.ByTitleAscending
            )
        val returnButton = rootView.findViewById<Button>(R.id.return_button)

        recyclerView.enableEdgeToEdgePaddingListener(ime = true)
        recyclerView.setAppBar(appBarLayout)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        editText.setText(searchTextFlow.value)
        editText.addTextChangedListener { rawText ->
            searchTextFlow.value = rawText?.toString() ?: ""
        }

        returnButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }

    override fun onPause() {
        if (!isHidden) {
            requireActivity().closeKeyboard(editText)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden && !mainActivity.playerBottomSheet.visibleAndExpanded) {
            requireActivity().showKeyboard(editText)
        } else {
            editText.clearFocus()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            requireActivity().closeKeyboard(editText)
            super.onHiddenChanged(true)
        } else {
            super.onHiddenChanged(false)
            requireActivity().showKeyboard(editText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycleScope.cancel() // TODO: why?
    }

}
