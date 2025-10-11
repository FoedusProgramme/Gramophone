package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
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
import uk.akane.libphonograph.items.Playlist

class PlaylistEditFragment : BaseFragment(false) {
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

		// TODO: if there is a pending playlist edit, ask whether it should be restored
		// TODO: maybe use something like PlaylistCardAdapter instead of SongAdapter? if I add
		//  drag handles and delete buttons to SongAdapter it would be kinda weird and sorting or
		//  layouts really aren't needed here...
		val songAdapter =
			SongAdapter(
				this,
				flowOf(), //songList,
				rawOrderExposed = Sorter.Type.NaturalOrder,
				isSubFragment = R.id.edit
			)

		recyclerView.enableEdgeToEdgePaddingListener()
		recyclerView.setAppBar(appBarLayout)
		recyclerView.adapter = songAdapter.concatAdapter

		// TODO: some sorta delete button for songs
		val callback = PlaylistCardMoveCallback { from, to ->
			/*val list = runBlocking { songList.first() }.toMutableList()
			val item = list.removeAt(from)
			list.add(to, item)
			songList.tryEmit(list)*/
			// TODO: store pending playlist
		}
		val touchHelper = ItemTouchHelper(callback)
		touchHelper.attachToRecyclerView(recyclerView)

		// Build FastScroller.
		recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

		topAppBar.setNavigationOnClickListener {
			// TODO: ask "are you sure" and delete pending edit
			requireActivity().supportFragmentManager.popBackStack()
		}

		return rootView
	}

	private class PlaylistCardMoveCallback(private val touchHelperContract: (Int, Int) -> Unit) :
		ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
		override fun isLongPressDragEnabled(): Boolean {
			return true
		}

		override fun isItemViewSwipeEnabled(): Boolean {
			return false
		}

		override fun onMove(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			target: RecyclerView.ViewHolder
		): Boolean {
			val vhBap = viewHolder.bindingAdapterPosition
			val tBap = target.bindingAdapterPosition
			if (vhBap != RecyclerView.NO_POSITION && tBap != RecyclerView.NO_POSITION) {
				touchHelperContract(vhBap, tBap)
				return true
			}
			return false
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			throw IllegalStateException()
		}
	}

}
