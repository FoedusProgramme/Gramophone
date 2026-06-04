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

import android.content.Context
import android.content.IntentSender
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.DateAdapter
import org.akanework.gramophone.ui.adapters.DetailedFolderAdapter
import org.akanework.gramophone.ui.adapters.GenreAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter

/**
 * AdapterFragment:
 *   This fragment is the container for any list that contains
 * recyclerview in the program.
 *
 * @author nift4
 */
class AdapterFragment : BaseFragment(null) {
    private lateinit var adapter: BaseInterface<*>
    private lateinit var recyclerView: MyRecyclerView
    private var pendingRequest: Bundle? = null
    private lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (savedInstanceState?.containsKey("pendingRequest") == true) {
            pendingRequest = savedInstanceState.getBundle("pendingRequest")
        }
        val rootView = inflater.inflate(R.layout.fragment_recyclerview, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerview)
        recyclerView.setRecycledViewPool((requireParentFragment() as ViewPagerFragment).recycledViewPool)
        recyclerView.enableEdgeToEdgePaddingListener()
        adapter = createAdapter(savedInstanceState)
        recyclerView.adapter = adapter.concatAdapter
        recyclerView.setAppBar((requireParentFragment() as ViewPagerFragment).appBarLayout)
        recyclerView.fastScroll(adapter, adapter.itemHeightHelper)
        (adapter as? RequestAdapter)?.let { it1 ->
            intentSender =
                registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it1.onRequest(it.resultCode, pendingRequest.also { pendingRequest = null }
                        ?: throw IllegalStateException("pendingRequest null, why?"))
                }
        }
        return rootView
    }

    override fun onDestroyView() {
        adapter.onFullyDrawnListener = null
        super.onDestroyView()
    }

    fun startRequest(sender: IntentSender, data: Bundle) {
        pendingRequest = data
        intentSender.launch(IntentSenderRequest.Builder(sender).build())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingRequest != null) {
            outState.putBundle("pendingRequest", pendingRequest)
        }
        if (adapter is DetailedFolderAdapter) {
            (adapter as DetailedFolderAdapter).onSaveInstanceState(outState)
        }
    }

    private fun createAdapter(savedInstanceState: Bundle?): BaseInterface<*> {
        val id = arguments?.getInt("ID", -1)
        val qTitle = getQueueTitle()
        return when (id) {
            R.id.songs -> SongAdapter(this, qTitle)
            R.id.albums -> AlbumAdapter(this, qTitle)
            R.id.artists -> ArtistAdapter(this)
            R.id.genres -> GenreAdapter(this)
            R.id.dates -> DateAdapter(this)
            R.id.folders -> DetailedFolderAdapter(this, false, savedInstanceState)
            R.id.detailed_folders -> DetailedFolderAdapter(this, true, savedInstanceState)
            R.id.playlists -> PlaylistAdapter(this)
            -1, null -> throw IllegalArgumentException("unset ID value")
            else -> throw IllegalArgumentException("invalid ID value")
        }.apply {
            onFullyDrawnListener =
                { (requireParentFragment() as ViewPagerFragment).maybeReportFullyDrawn(id) }
        }
    }

    fun getQueueTitle(): Flow<String> {
        val stringId = when (arguments?.getInt("ID", -1)) {
            R.id.songs -> R.string.category_songs
            R.id.albums -> R.string.category_albums
            R.id.artists -> R.string.category_artists
            R.id.genres -> R.string.category_genres
            R.id.dates -> R.string.category_dates
            R.id.folders -> R.string.folders
            R.id.detailed_folders -> R.string.folders
            R.id.playlists -> R.string.category_playlists
            else -> null
        }
        if (stringId == null) return flowOf("MISSING TITLE (AdapterFragment)")
        return flowOf(requireContext().getString( stringId))
    }

    abstract class BaseInterface<T : RecyclerView.ViewHolder>
        : MyRecyclerView.Adapter<T>(), PopupTextProvider {
        abstract val concatAdapter: ConcatAdapter
        abstract val itemHeightHelper: ItemHeightHelper?
        var onFullyDrawnListener: (() -> Unit)? = null
        protected fun reportFullyDrawn() {
            onFullyDrawnListener?.invoke()
            onFullyDrawnListener = null
        }

        // for decor
        abstract val context: Context
        abstract val layoutInflater: LayoutInflater
        abstract val canChangeLayout: Boolean
        abstract val sortType: StateFlow<Sorter.Type>
        abstract val sortTypes: Set<Sorter.Type>
        abstract var layoutType: BaseAdapter.LayoutType?
        abstract fun sort(type: Sorter.Type)
        abstract val itemCountForDecor: Int
    }

    interface RequestAdapter {
        fun onRequest(resultCode: Int, data: Bundle)
    }
}
