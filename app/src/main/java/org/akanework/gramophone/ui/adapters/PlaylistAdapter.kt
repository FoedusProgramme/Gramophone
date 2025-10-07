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

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.DialogCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.iterator
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.ItemManipulator.DeleteFailedPleaseTryDeleteRequestException
import java.io.File

/**
 * [PlaylistAdapter] is an adapter for displaying artists.
 */
class PlaylistAdapter(
    fragment: AdapterFragment,
) : BaseAdapter<Playlist>
    (
    fragment,
    liveData = (fragment.requireActivity() as MainActivity).reader.playlistListFlow,
    sortHelper = StorePlaylistHelper,
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    defaultLayoutType = LayoutType.LIST
), AdapterFragment.RequestAdapter {
    override val defaultCover = R.drawable.ic_default_cover_playlist
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun virtualTitleOf(item: Playlist): String {
        return when (item) {
            is RecentlyAdded -> context.getString(R.string.recently_added)
            is Favorite -> context.getString(R.string.playlist_favourite)
            else -> {
                context.getString(R.string.unknown_playlist) + " (${item.id} - ${item.path})"
            }
        }
    }

    override fun coverOf(item: Playlist): Uri? {
        return if (item.id != null) super.coverOf(item) else
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .path(when (item) {
                    is RecentlyAdded -> R.drawable.ic_default_cover_playlist_recently
                    is Favorite -> R.drawable.ic_default_cover_playlist_favorite
                    else -> R.drawable.ic_default_cover_playlist
                }.toString()).build()
    }

    override fun onClick(item: Playlist) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putString("Class", item.javaClass.name) // TODO kinda stupid
            putString("Id", item.id?.toString())
            putInt("Item", R.id.playlist)
        }
    }

    override fun onMenu(item: Playlist, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        val canEdit = Flags.PLAYLIST_EDITING!! && item.id != null
        popupMenu.menu.iterator().forEach {
            it.isVisible = it.itemId == R.id.play_next || it.itemId == R.id.add_to_queue
                    || (canEdit && (it.itemId == R.id.rename || it.itemId == R.id.delete))
        }
        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                }

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        item.songList,
                    )
                }

                R.id.delete -> {
                    if (item.id == null) {
                        Toast.makeText(context, context.getString(
                            R.string.delete_failed_playlist, "item.id == null"),
                            Toast.LENGTH_LONG).show()
                        return@setOnMenuItemClickListener true
                    }
                    val res = ItemManipulator.deletePlaylist(context, item.id!!)
                    if (res.continueAction != null) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.delete)
                            .setMessage(context.getString(R.string.delete_really, item.title))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        res.continueAction.invoke()
                                    } catch (e: DeleteFailedPleaseTryDeleteRequestException) {
                                        withContext(Dispatchers.Main) {
                                            mainActivity.intentSender.launch(
                                                IntentSenderRequest.Builder(e.pendingIntent).build()
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PlaylistAdapter", Log.getThrowableString(e)!!)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(
                                                R.string.delete_failed_playlist, e.javaClass.name + ": " + e.message),
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        mainActivity.intentSender.launch(
                            IntentSenderRequest.Builder(res.startSystemDialog!!).build()
                        )
                    }
                }

                R.id.rename -> {
                    if (item.id == null) {
                        Toast.makeText(context, context.getString(R.string.rename_failed_playlist, "$item"), Toast.LENGTH_LONG).show()
                        return@setOnMenuItemClickListener true
                    }
                    playlistNameDialog(context, R.string.rename_playlist, item.title ?: "") { name ->
                        val id = item.id!!
                        val uri = ContentUris.withAppendedId(
                            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
                        )
                        val data = Bundle().apply {
                            putLong("Id", id)
                            putString("NewName", name)
                            putString("Path", item.path!!.absolutePath)
                        }
                        CoroutineScope(Dispatchers.Default).launch {
                            if (ItemManipulator.needRequestWrite(context, uri)) {
                                val pendingIntent = MediaStore.createWriteRequest(
                                    context.contentResolver,
                                    listOf(uri)
                                )
                                (fragment as AdapterFragment).startRequest(
                                    pendingIntent.intentSender,
                                    data
                                )
                            } else {
                                withContext(Dispatchers.Main) {
                                    onRequest(Activity.RESULT_OK, data)
                                }
                            }
                        }
                    }
                }

                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onRequest(resultCode: Int, data: Bundle) {
        if (resultCode == Activity.RESULT_OK) {
            val path = data.getString("Path")!!
            val newName = data.getString("NewName")!!
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    ItemManipulator.renamePlaylist(context, File(path), newName)
                } catch (e: DeleteFailedPleaseTryDeleteRequestException) {
                    withContext(Dispatchers.Main) {
                        mainActivity.intentSender.launch(
                            IntentSenderRequest.Builder(e.pendingIntent).build()
                        )
                    }
                } catch (e: Exception) {
                    Log.e("PlaylistAdapter", Log.getThrowableString(e)!!)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(
                            R.string.rename_failed_playlist, e.javaClass.name + ": " + e.message),
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.rename_failed_playlist,
                "$resultCode"), Toast.LENGTH_LONG).show()
        }
    }

    override fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<Playlist>> {
        return PlaylistDecorAdapter(this)
    }

    private inner class PlaylistDecorAdapter(
        playlistAdapter: PlaylistAdapter
    ) : BaseDecorAdapter<PlaylistAdapter>(playlistAdapter, R.plurals.items) {

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
            payloads: List<Any?>
        ) {
            super.onBindViewHolder(holder, position, payloads)
            if (!Flags.PLAYLIST_EDITING!!) return
            holder.createPlaylist.visibility = View.VISIBLE
            holder.createPlaylist.setOnClickListener { _ ->
                playlistNameDialog(context, R.string.create_playlist, "") { name ->
                    ioScope.launch {
                        try {
                            ItemManipulator.createPlaylist(context, name)
                        } catch (e: Exception) {
                            Log.e("PlaylistAdapter", Log.getThrowableString(e)!!)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(
                                    R.string.create_failed_playlist, e.javaClass.name + ": " + e.message),
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.createPlaylist.visibility = View.GONE
            holder.createPlaylist.setOnClickListener(null)
            super.onViewRecycled(holder)
        }
    }

    object StorePlaylistHelper : StoreItemHelper<Playlist>()

    companion object {
        fun playlistNameDialog(context: Context, title: Int, initialValue: String, then: (String) -> Unit) {
            val d = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(R.layout.dialog_new_playlist)
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    val et = DialogCompat.requireViewById(d as AlertDialog, R.id.editText) as TextInputEditText
                    val name = et.editableText.toString()
                    then(name)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
            val et = DialogCompat.requireViewById(d, R.id.editText) as TextInputEditText
            val b = d.getButton(DialogInterface.BUTTON_POSITIVE)
            val inL = DialogCompat.requireViewById(d, R.id.inputLayout) as TextInputLayout
            et.editableText.append(initialValue)
            b.isEnabled = !initialValue.isBlank()
            inL.error = null
            d.window!!.decorView.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            // TODO: why on earth is this even needed? "Small Phone" emu otherwise cant type
            d.window!!.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, d.window!!.decorView.measuredHeight)
            et.addTextChangedListener(afterTextChanged = {
                val tmp = et.editableText.toString()
                val hasForbidden = tmp.any { it in "/\\:*?\"<>|" || it.code <= 0x1F || it.code == 0x7F }
                if (hasForbidden) {
                    inL.error = context.getString(R.string.forbidden_symbol_error)
                } else {
                    inL.error = null
                }
                b.isEnabled = !tmp.isBlank() && !hasForbidden
            })
            et.requestFocus()
            et.post {
                if (ViewCompat.getRootWindowInsets(d.window!!.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == false
                ) {
                    WindowInsetsControllerCompat(d.window!!, et).show(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }
}
