package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.IOException
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.components.EditSongAdapter
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.toUriCompat
import java.nio.file.Files

class PlaylistEditFragment : BaseFragment(false) {
    companion object {
        private const val TAG = "PlaylistEditFragment"
        private const val FOLDER_NAME = "Restored playlists"
    }
    private lateinit var theItem: MutableSharedFlow<Playlist?>
    private lateinit var tmpName: String
    private lateinit var loadingCircle: View
    private lateinit var recyclerView: MyRecyclerView
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var adapter: PlaylistEditAdapter
    private lateinit var uri: Uri
    private var doneEditing = false
    private var entries = MutableStateFlow(0 to PlaylistSerializer.Playlist.create())
    private var renderedEntries = mapOf<PlaylistSerializer.Entry, MediaItem>()
    private lateinit var qTitle: Flow<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        intentSender =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                onRequest(it.resultCode)
            }
        theItem = MutableSharedFlow(replay = 1)
        qTitle = theItem.map { it?.title ?: "MISSING TITLE (PlaylistEditFragment)" }

        val rootView = inflater.inflate(R.layout.fragment_edit_playlist, container, false)
        topAppBar = rootView.findViewById(R.id.topAppBar)
        val collapsingToolbarLayout =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        recyclerView = rootView.findViewById(R.id.recyclerview)
        loadingCircle = rootView.findViewById(R.id.loadingCircle)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        topAppBar.setNavigationIcon(R.drawable.outline_close_24)
        topAppBar.inflateMenu(R.menu.playlist_apply_menu)
        appBarLayout.enableEdgeToEdgePaddingListener()

        adapter = PlaylistEditAdapter()
        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        recyclerView.adapter = adapter
        touchHelper = ItemTouchHelper(adapter.PlaylistCardMoveCallback())
        touchHelper.attachToRecyclerView(recyclerView)

        // Build FastScroller.
        recyclerView.fastScroll(null, null)

        topAppBar.setNavigationOnClickListener {
            maybeGoBack()
        }

        val application = requireContext().applicationContext
        val bundle = requireArguments()
        val id = bundle.getString("Id")!!.toLong()
        uri = ContentUris.withAppendedId(@Suppress("deprecation")
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id)
        lifecycleScope.launch(Dispatchers.Default) {
            val item = mainActivity.reader.playlistListFlow.map {
                it.find { p -> p.id == id }
            }.first()
            if (item == null || item.path == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        R.string.unknown_playlist,
                        Toast.LENGTH_LONG
                    ).show()
                    requireActivity().supportFragmentManager.popBackStack()
                }
                return@launch
            }
            theItem.emit(item)
            collapsingToolbarLayout.title = if (item is Favorite)
                context?.getString(R.string.playlist_favourite) else item.title
                ?: context?.getString(R.string.unknown_playlist)
            // User can visit the folder in DocsUI to get old cached versions if needed
            tmpName = "${item.path.name}_${item.path.lastModified()}.xspf"
            val hasRestorablePlaylist = try {
                hasChanges(application)
            } catch (e: Exception) {
                Log.e(TAG, "failed to check for changes", e)
                null
            }
            when (hasRestorablePlaylist) {
                true -> {
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.restore_edits)
                            .setMessage(
                                requireContext().getString(
                                    R.string.restore_edits_msg,
                                    item.title.toString()
                                )
                            )
                            .setPositiveButton(R.string.restore) { _, _ ->
                                lifecycleScope.launch(Dispatchers.Default) {
                                    load(application, true)
                                }
                            }
                            .setNegativeButton(R.string.discard) { _, _ ->
                                lifecycleScope.launch(Dispatchers.Default) {
                                    try {
                                        discardChanges(application)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "failed to discard", e)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                requireContext(),
                                                R.string.mount_storage,
                                                Toast.LENGTH_LONG
                                            ).show()
                                            requireActivity().supportFragmentManager.popBackStack()
                                        }
                                        return@launch
                                    }
                                    load(application, false)
                                }
                            }
                            .setNeutralButton(android.R.string.cancel) { _, _ ->
                                requireActivity().supportFragmentManager.popBackStack()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
                false -> {
                    load(application, false)
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(), R.string.mount_storage,
                            Toast.LENGTH_LONG
                        ).show()
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }
            }
        }

        return rootView
    }

    private suspend fun CoroutineScope.load(context: Context, restore: Boolean) {
        val pathMap = mainActivity.reader.pathMapFlow.first()
        val readback = try {
            if (restore) {
                (try {
                    readChanges(context)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to restore changes", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(), context.getString(
                                R.string.failed_to_restore, e.message),
                            Toast.LENGTH_LONG).show()
                    }
                    Reader.readPlaylist(context, uri)
                })
            } else Reader.readPlaylist(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "failed to read changes", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), e.toString(),
                    Toast.LENGTH_LONG).show()
                requireActivity().supportFragmentManager.popBackStack()
            }
            return
        }.let { playlist ->
            playlist.copy(entries = playlist.entries.map {
                it.updateFromMediaItem(pathMap)
            })
        }
        if (!restore && readback.entries.isEmpty()) {
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.playlist_empty)
                    .setMessage(R.string.playlist_empty_msg)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    .setOnCancelListener {
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    .show()
            }
            return
        }
        val rendered = hashMapOf<PlaylistSerializer.Entry, MediaItem>()
        readback.entries.forEach {
            rendered[it] = it.resolveMediaItem(pathMap) ?:
                    MediaItem.Builder().setMediaId("Missing:${it.locations.firstOrNull()}")
                        .setUri(it.locations.firstOrNull())
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setTitle("${it.locations.firstOrNull()?.lastPathSegment}")
                            .setArtist("${it.locations.firstOrNull()}")
                            .build())
                        .build()
        }
        val renderedFinal = rendered.toMap()
        withContext(Dispatchers.Main) {
            entries.value = 1 to readback
            renderedEntries = renderedFinal
            requestWriteIfNeeded()
            // Only enable saving after loading the actual content
            topAppBar.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.apply -> {
                        if (!doneEditing)
                             commitAndQuit()
                        true
                    }

                    else -> false
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(
                this@PlaylistEditFragment) { maybeGoBack() }
            @SuppressLint("NotifyDataSetChanged") adapter.notifyDataSetChanged()
            recyclerView.doOnLayout {
                recyclerView.postOnAnimation {
                    loadingCircle.visibility = View.GONE
                }
            }
        }
        launch {
            mainActivity.reader.pathMapFlow.drop(1).collectLatest { pathMap ->
                // Keep using the old readback set because it will be a superset of current
                // entries, because there's no way to add new songs.
                val oldRendered = renderedEntries
                readback.entries.forEach {
                    rendered[it] = it.resolveMediaItem(pathMap) ?: (if (rendered[it]!!
                            .mediaId.startsWith("Missing:")) rendered[it]!! else
                        MediaItem.Builder().setMediaId("Missing:${it.locations.firstOrNull()}")
                            .setUri(it.locations.firstOrNull())
                            .setMediaMetadata(MediaMetadata.Builder()
                                .setTitle("${it.locations.firstOrNull()?.lastPathSegment}")
                                .setArtist("${it.locations.firstOrNull()}")
                                .build())
                            .build())
                }
                val renderedFinal = rendered.toMap()
                while (true) {
                    val entriesTmp = entries.value
                    val oldGlued = entriesTmp.second.entries.map { oldRendered[it]!! }
                    val newGlued = entriesTmp.second.entries.map { renderedFinal[it]!! }
                    val diffs = oldGlued.mapIndexedNotNull { i, old ->
                        if (old == newGlued[i])
                            null
                        else i
                    }
                    // keep same generation number as this isn't a user triggered edit
                    val newEntries = entriesTmp.first to entriesTmp.second.let { playlist ->
                        playlist.copy(entries = playlist.entries.map {
                            it.updateFromMediaItem(pathMap)
                        })
                    }
                    val doBreak = withContext(Dispatchers.Main) {
                        if (entries.value.first == entriesTmp.first) {
                            entries.value = newEntries
                            renderedEntries = renderedFinal
                            for (pos in diffs) {
                                adapter.notifyItemChanged(pos)
                            }
                            true
                        } else false // User changed something and raced with us, try again
                    }
                    if (doBreak) {
                        break
                    }
                }
            }
        }
        var generation = 1
        entries.drop(1).collectLatest { new ->
            if (!doneEditing && new.first > generation) {
                generation = new.first
                try {
                    writeChanges(context, new.second)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to write changes", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), e.toString(),
                            Toast.LENGTH_LONG).show()
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    return@collectLatest
                }
            }
        }
    }

    private fun maybeGoBack() {
        if (doneEditing) {
            // invalid state, maybe user pressed back while we are still saving
            return
        }
        if (entries.value.first <= 1) { // nothing was changed
            requireActivity().supportFragmentManager.popBackStack()
            return
        }
        val context = requireContext()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.discard_changes_title)
            .setMessage(
                context.getString(
                    R.string.discard_changes_msg,
                    runBlocking { theItem.first() }?.title.toString()
                )
            )
            .setPositiveButton(R.string.save) { _, _ ->
                commitAndQuit()
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        discardChanges(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "failed to discard", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(),
                                R.string.mount_storage,
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                requireActivity().supportFragmentManager.popBackStack()
            }
            .setNeutralButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun commitAndQuit() {
        val context = requireContext()
        val songs = entries.value.second
        doneEditing = true
        CoroutineScope(Dispatchers.Default).launch {
            val ok = try {
                ItemManipulator.setPlaylistContent(context, uri, songs, false)
                true
            } catch (e: Exception) {
                Log.e("PlaylistEditFragment", "failed to edit $uri", e)
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), context.getString(R
                        .string.edit_playlist_failed, e.message),
                        Toast.LENGTH_LONG).show()
                    doneEditing = false // allow user to save again to try again
                }
                false
            }
            if (ok) {
                try {
                    discardChanges(context)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to discard", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), R.string.mount_storage,
                            Toast.LENGTH_LONG).show()
                    }
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
        }
    }

    private fun hasChanges(context: Context): Boolean? {
        val filesDir = context.externalCacheDir ?: return null
        val playlistsDir = filesDir.resolve(FOLDER_NAME)
        if (!playlistsDir.exists() && !playlistsDir.mkdirs())
            return null
        val playlist = playlistsDir.resolve(tmpName)
        return playlist.exists()
    }

    private fun readChanges(context: Context): PlaylistSerializer.Playlist {
        val playlistsDir = context.externalCacheDir!!.resolve(FOLDER_NAME)
        val playlist = playlistsDir.resolve(tmpName)
        return PlaylistSerializer.read(playlist)
    }

    private fun writeChanges(context: Context, entries: PlaylistSerializer.Playlist) {
        val playlistsDir = context.externalCacheDir!!.resolve(FOLDER_NAME)
        val playlist = playlistsDir.resolve(tmpName)
        PlaylistSerializer.write(context, playlist, playlist.toUriCompat(),
            entries)
    }

    private suspend fun discardChanges(context: Context) {
        val filesDir = context.externalCacheDir
        if (filesDir == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), R.string.mount_storage,
                    Toast.LENGTH_LONG).show()
            }
            return
        }
        val playlistsDir = filesDir.resolve(FOLDER_NAME)
        val playlist = playlistsDir.resolve(tmpName)
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.delete(playlist.toPath())
                } catch (e: Exception) {
                    throw IOException("Failed to delete $playlist", e)
                }
            } else {
                if (!playlist.delete())
                    throw IOException("Failed to delete $playlist (no details available)")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (entries.value.first > 0)
            requestWriteIfNeeded()
    }

    fun requestWriteIfNeeded() {
        val context = requireContext().applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val token = MediaStoreCompat.needRequestBytesWrite(context, uri)
            if (token != null) {
                val pi = MediaStoreCompat.createWriteRequest(context, listOf(token))
                withContext(Dispatchers.Main) {
                    intentSender.launch(IntentSenderRequest.Builder(pi).build())
                }
            }
        }
    }

    private fun onRequest(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            // If there are any saved edits, don't commit (we can't, user said no) nor discard them
            requireActivity().supportFragmentManager.popBackStack()
            return
        }
    }

    private inner class PlaylistEditAdapter : EditSongAdapter(requireContext(), false) {
        override fun getItemCount(): Int {
            return entries.value.second.entries.size
        }

        override fun startDrag(holder: ViewHolder) {
            touchHelper.startDrag(holder)
        }

        override fun onClick(pos: Int) {
            // do nothing
        }

        override fun getItem(pos: Int): MediaItem {
            return renderedEntries[entries.value.second.entries[pos]]!!
        }

        override fun onRowMoved(from: Int, to: Int) {
            entries.update { i ->
                i.first + 1 to i.second.copy(entries = i.second.entries.toMutableList().also {
                    it.add(to, it.removeAt(from))
                })
            }
            notifyItemMoved(from, to)
        }

        override fun removeItem(pos: Int) {
            entries.update { i ->
                i.first + 1 to i.second.copy(entries = i.second.entries.toMutableList().also {
                    it.removeAt(pos)
                })
            }
            notifyItemRemoved(pos)
        }

        override fun getCoverFallback(pos: Int): Int {
            if (getItem(pos).mediaId.startsWith("Missing:"))
                return R.drawable.ic_default_cover_error
            return R.drawable.ic_default_cover
        }
    }
}
