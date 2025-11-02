package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter
import org.akanework.gramophone.ui.components.EditSongAdapter
import uk.akane.libphonograph.items.Playlist

class PlaylistEditFragment : BaseFragment(false) {
    private lateinit var touchHelper: ItemTouchHelper

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View? {
		val theItem = MutableSharedFlow<Playlist?>(replay = 1)

		val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
		val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
		val collapsingToolbarLayout =
			rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
		val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
		val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
		topAppBar.setNavigationIcon(R.drawable.outline_close_24)
		appBarLayout.enableEdgeToEdgePaddingListener()

		val bundle = requireArguments()
		val clazz = bundle.getString("Class")
		val id = bundle.getString("Id")?.toLong()
		lifecycleScope.launch(Dispatchers.Default) {
			val item = mainActivity.reader.playlistListFlow.map {
				it.find { it.id == id && it.javaClass.name == clazz }
			}.first()
			collapsingToolbarLayout.title = item?.title
				?: context?.getString(R.string.unknown_playlist)
			theItem.emit(item)
		}

		// TODO(ASAP): if there is a pending playlist edit, ask whether it should be restored
        val adapter = PlaylistEditAdapter()
		recyclerView.enableEdgeToEdgePaddingListener()
		recyclerView.setAppBar(appBarLayout)
		recyclerView.adapter = adapter
		touchHelper = ItemTouchHelper(adapter.PlaylistCardMoveCallback())
		touchHelper.attachToRecyclerView(recyclerView)

		// Build FastScroller.
		recyclerView.fastScroll(null, null)

		topAppBar.setNavigationOnClickListener {
			// TODO(ASAP): ask "are you sure" and delete pending edit
			requireActivity().supportFragmentManager.popBackStack()
		}

		return rootView
	}

    // TODO(ASAP): finish it
    private inner class PlaylistEditAdapter : EditSongAdapter(requireContext()) {
        override fun getItemCount(): Int {
            TODO("Not yet implemented")
        }

        override fun startDrag(holder: ViewHolder) {
            touchHelper.startDrag(holder)
        }

        override fun onClick(pos: Int) {
            TODO("Not yet implemented")
        }

        override fun getItem(pos: Int): MediaItem {
            TODO("Not yet implemented")
        }

        override fun onRowMoved(from: Int, to: Int) {
            TODO("Not yet implemented")
        }

        override fun removeItem(pos: Int) {
            TODO("Not yet implemented")
        }
    }
}
