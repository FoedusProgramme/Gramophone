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
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.adapters.SongAdapter

/**
 * SearchFragment:
 *   A fragment that contains a search bar which browses
 * the library finding items matching user input.
 *
 * @author AkaneTan
 */
class SearchFragment : BaseFragment(true) {
    // TODO this class leaks InsetSourceControl
    private val filteredList: MutableList<MediaItem> = mutableListOf()
    private lateinit var editText: EditText
    private lateinit var searchModeToggle: MaterialButtonToggleGroup
    private var currentMode = SearchMode.SONGS

    enum class SearchMode {
        SONGS, LYRICS
    }

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
        searchModeToggle = rootView.findViewById(R.id.search_mode_toggle)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val songList = MutableStateFlow(listOf<MediaItem>())
        val songAdapter =
            SongAdapter(
                this, songList,
                true, null, false, isSubFragment = R.id.search,
                allowDiffUtils = true, rawOrderExposed = true
            )
        val returnButton = rootView.findViewById<Button>(R.id.return_button)

        recyclerView.enableEdgeToEdgePaddingListener(ime = true)
        recyclerView.setAppBar(appBarLayout)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        // Set up search mode toggle listener
        searchModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = if (checkedId == R.id.button_lyrics) SearchMode.LYRICS else SearchMode.SONGS
                // Update search hint based on mode
                editText.hint = if (currentMode == SearchMode.LYRICS) {
                    getString(R.string.search_lyrics_hint)
                } else {
                    getString(R.string.search)
                }
                // Re-run search with current text if any
                val currentText = editText.text?.toString()
                if (!currentText.isNullOrBlank()) {
                    performSearch(currentText, songList)
                }
            }
        }

        editText.addTextChangedListener { rawText ->
            val text = rawText?.toString() ?: ""
            performSearch(text, songList)
        }

        returnButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }

    private fun performSearch(text: String, songList: MutableStateFlow<List<MediaItem>>) {
        if (text.isBlank()) {
            songList.value = listOf()
            return
        }

        // Launch a coroutine for searching in the library.
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            // Clear the list from the last search.
            filteredList.clear()
            
            if (currentMode == SearchMode.LYRICS) {
                // Search lyrics content
                filteredList.addAll(searchInLyrics(text))
            } else {
                // Search song metadata (existing functionality)
                filteredList.addAll(mainActivity.reader.songListFlow.first().filter {
                    val isMatchingTitle = it.mediaMetadata.title?.contains(text, true) == true
                    val isMatchingAlbum =
                        it.mediaMetadata.albumTitle?.contains(text, true) == true
                    val isMatchingArtist =
                        it.mediaMetadata.artist?.contains(text, true) == true
                    isMatchingTitle || isMatchingAlbum || isMatchingArtist
                })
            }
            
            withContext(Dispatchers.Main) {
                songList.value = filteredList.toList()
            }
        }
    }

    private suspend fun searchInLyrics(searchText: String): List<MediaItem> {
        val allSongs = mainActivity.reader.songListFlow.first()
        val matchingSongs = mutableListOf<MediaItem>()
        
        // Limit search to prevent performance issues with large libraries
        val maxSongsToSearch = 1000
        val songsToSearch = if (allSongs.size > maxSongsToSearch) {
            allSongs.take(maxSongsToSearch)
        } else {
            allSongs
        }
        
        for (song in songsToSearch) {
            try {
                val lyricsParserOptions = LrcUtils.LrcParserOptions(
                    trim = true,
                    multiLine = false,
                    errorText = null
                )
                
                // TODO: Add embedded lyrics search in future enhancement
                // Note: We'd need access to track metadata here, but for now
                // we'll focus on file-based lyrics which are more common
                
                // Load from external lyrics files
                val lyrics = LrcUtils.loadAndParseLyricsFile(song.getFile(), lyricsParserOptions)
                
                // Check if lyrics contain the search text
                val lyricsText = lyrics?.getAllLyricsText()
                if (lyricsText?.contains(searchText, ignoreCase = true) == true) {
                    matchingSongs.add(song)
                }
            } catch (e: java.io.IOException) {
                // Skip songs with file I/O errors (missing lyrics files, etc.)
                continue
            } catch (e: IllegalArgumentException) {
                // Skip songs with parsing errors (malformed lyrics files)
                continue
            }
        }
        
        return matchingSongs
    }

    // Extension function to get all text from lyrics
    private fun SemanticLyrics.getAllLyricsText(): String {
        return when (this) {
            is SemanticLyrics.UnsyncedLyrics -> {
                unsyncedText.joinToString("\n") { it.first }
            }
            is SemanticLyrics.SyncedLyrics -> {
                text.joinToString("\n") { it.text }
            }
        }
    }

    override fun onPause() {
        if (!isHidden) {
            requireActivity().closeKeyboard(editText)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            requireActivity().showKeyboard(editText)
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
        viewLifecycleOwner.lifecycleScope.cancel()
    }

}
