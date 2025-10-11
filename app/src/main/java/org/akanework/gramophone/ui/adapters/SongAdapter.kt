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

package org.akanework.gramophone.ui.adapters

import android.net.Uri
import androidx.media3.common.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.albumYear
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.modifiedDate
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File
import java.util.GregorianCalendar

/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    fragment: Fragment,
    songList: Flow<List<MediaItem>?> = (fragment.requireActivity() as MainActivity).reader.songListFlow,
    helper: Sorter.NaturalOrderHelper<MediaItem>? = null,
    isSubFragment: Int? = null,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Sorter.Type? = if (isSubFragment == null) Sorter.Type.ByTitleAscending else null,
    val folder: Boolean = false
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper,
    naturalOrderHelper = helper,
    initialSortType =
        (if (helper != null) Sorter.Type.NaturalOrder else (rawOrderExposed ?:
        if (folder) Sorter.Type.ByFilePathAscending else Sorter.Type.ByTitleAscending)),
    canSort = true,
    pluralStr = R.plurals.songs,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils
) {

    fun getSongList() = list?.second ?: emptyList()

    fun getActivity() = mainActivity

    private val mediaControllerViewModel: MediaControllerViewModel by fragment.activityViewModels()
    private var idToPosMap: HashMap<String, Int>? = null
    private var currentMediaItem: String? = null
        set(value) {
            if (field != value) {
                val oldValue = field
                field = value
                if (idToPosMap != null) {
                    val oldPos = idToPosMap!![oldValue]
                    val newPos = idToPosMap!![value]
                    if (oldPos != null) {
                        notifyItemChanged(oldPos, true)
                    }
                    if (newPos != null) {
                        notifyItemChanged(newPos, true)
                    }
                }
            }
        }
    private var currentIsPlaying: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && currentMediaItem != null) {
                    idToPosMap?.get(currentMediaItem)?.let {
                        notifyItemChanged(it, false)
                    }
                }
            }
        }

    init {
        mediaControllerViewModel.addRecreationalPlayerListener(
            fragment.viewLifecycleOwner.lifecycle
        ) {
            currentMediaItem = it.currentMediaItem?.mediaId
            currentIsPlaying =
                it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentMediaItem = mediaItem?.mediaId
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    currentIsPlaying =
                        playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    currentIsPlaying =
                        it.playWhenReady && playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }
            }
        }
    }

    override fun onListUpdated() {
        // TODO run this method on a different thread / in advance
        idToPosMap = hashMapOf()
        list!!.second.forEachIndexed { i, item -> idToPosMap!![item.mediaId] = i }
    }

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    override fun titleOf(item: MediaItem): String? {
        return if (folder) item.getFile()?.name else super.titleOf(item)
    }

    override fun onClick(item: MediaItem) {
        val mediaController = mainActivity.getPlayer()
        mediaController?.apply {
            val songList = getSongList()
            setMediaItems(songList, songList.indexOf(item), C.TIME_UNSET)
            prepare()
            play()
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        if (!Flags.PLAYLIST_EDITING!!)
            popupMenu.menu.findItem(R.id.add_to_playlist).isVisible = false

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        mediaController.currentMediaItemIndex + 1,
                        item,
                    )
                    true
                }

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        item,
                    )
                    true
                }

                R.id.album -> {
                    mainActivity.startFragment(GeneralSubFragment()) {
                        putString("Id", item.mediaMetadata.albumId?.toString())
                        putInt("Item", R.id.album)
                    }
                    true
                }

                R.id.artist -> {
                    mainActivity.startFragment(ArtistSubFragment()) {
                        putString("Id", item.mediaMetadata.artistId?.toString())
                        putInt("Item", R.id.artist)
                    }
                    true
                }

                R.id.details -> {
                    mainActivity.startFragment(DetailDialogFragment()) {
                        putString("Id", item.mediaId)
                    }
                    true
                }

                R.id.delete -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val res = ItemManipulator.deleteSong(
                            context,
                            item.getFile()!!,
                            item.requireMediaStoreId()
                        )
                        if (res.continueAction != null) {
                            withContext(Dispatchers.Main) {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.delete)
                                    .setMessage(
                                        context.getString(
                                            R.string.delete_really,
                                            item.mediaMetadata.title
                                        )
                                    )
                                    .setPositiveButton(R.string.yes) { _, _ ->
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                res.continueAction.invoke()
                                            } catch (e: ItemManipulator.DeleteFailedPleaseTryDeleteRequestException) {
                                                withContext(Dispatchers.Main) {
                                                    mainActivity.intentSender.launch(
                                                        IntentSenderRequest.Builder(e.pendingIntent)
                                                            .build()
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SongAdapter", Log.getThrowableString(e)!!)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context, context.getString(
                                                            R.string.delete_failed,
                                                            e.javaClass.name + ": " + e.message
                                                        ),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton(R.string.no) { _, _ -> }
                                    .show()
                            }
                        } else {
                            mainActivity.intentSender.launch(
                                IntentSenderRequest.Builder(res.startSystemDialog!!).build()
                            )
                        }
                    }
                    true
                }

                R.id.share -> {
                    val uri = item.requestMetadata.mediaUri
                        ?: item.localConfiguration?.uri
                        ?: return@setOnMenuItemClickListener true

                    val mimeType = item.localConfiguration?.mimeType ?: "audio/*"

                    try {
                        val contentUri = if (uri.scheme == "file") {
                            FileProvider.getUriForFile(
                                mainActivity,
                                "${mainActivity.packageName}.fileProvider",
                                File(uri.path!!)
                            )
                        } else uri

                        ShareCompat.IntentBuilder(mainActivity)
                            .setType(mimeType)
                            .setStream(contentUri)
                            .setChooserTitle("Share audio file")
                            .startChooser()
                    } catch (e: Exception) {
                        Toast.makeText(
                            mainActivity,
                            "Unable to share: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                R.id.add_to_playlist -> {
                    mainActivity.addToPlaylistDialog(item.getFile())
                    true
                }

                else -> false
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            if (payloads.none { it is Boolean && it }) {
                holder.nowPlaying.drawable.level = if (currentIsPlaying == true) 1 else 0
                return
            }
            if (currentMediaItem == null || getSongList()[position].mediaId != currentMediaItem) {
                (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                    holder.nowPlaying.visibility = View.GONE
                    holder.nowPlaying.setImageDrawable(null)
                }
                holder.nowPlaying.drawable?.level = 2
                return
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
            if (currentMediaItem == null || getSongList().getOrNull(position)?.mediaId != currentMediaItem)
                return
        }
        holder.nowPlaying.setImageDrawable(NowPlayingDrawable(context)
            .also { it.level = if (currentIsPlaying == true) 1 else 0 })
        holder.nowPlaying.visibility = View.VISIBLE
    }

    object MediaItemHelper : Sorter.Helper<MediaItem>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAlbumArtistYearDescending, Sorter.Type.ByAlbumArtistYearAscending,
            Sorter.Type.ByAlbumYearDescending, Sorter.Type.ByAlbumYearAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByReleaseDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
            Sorter.Type.ByFilePathDescending, Sorter.Type.ByFilePathAscending,
            Sorter.Type.ByDiscAndTrack
        )
    ) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getFile(item: MediaItem): File {
            return item.getFile()!!
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getAlbumYear(item: MediaItem): Long? {
            return item.mediaMetadata.albumYear
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getDiscAndTrack(item: MediaItem): Int {
            return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber
                ?: 0)
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.addDate ?: -1
        }

        override fun getReleaseDate(item: MediaItem): Long {
            if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
                && item.mediaMetadata.releaseDay == null
            ) {
                return GregorianCalendar(
                    item.mediaMetadata.recordingYear ?: 0,
                    (item.mediaMetadata.recordingMonth ?: 1) - 1,
                    item.mediaMetadata.recordingDay ?: 0, 0, 0, 0
                )
                    .timeInMillis
            }
            return GregorianCalendar(
                item.mediaMetadata.releaseYear ?: 0,
                (item.mediaMetadata.releaseMonth ?: 1) - 1,
                item.mediaMetadata.releaseDay ?: 0, 0, 0, 0
            )
                .timeInMillis
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.modifiedDate ?: -1
        }
    }
}