/*
 *     Copyright (C) 2025 OuterTune Project
 *                   2026 The Gramophone contributors
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

package org.akanework.gramophone.logic

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.compose.ui.util.fastFirstOrNull
import androidx.core.os.BundleCompat
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.MediaItemList

private const val QUEUE_EXPIRY_MS = 10 * 36000000 // 10 hrs

/**
 * Multiple queues manager for inactive queues.
 *
 * Queue pinning:
 *  See [MultiQueueObject.expiry] for more details.
 *
 *  The active queue is managed by [org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer].
 *  When an active queue is unpinned, it will not be eligible for deletion. The expiry will be renewed
 *  once it becomes an inactive queue.
 *
 * Queue originality status:
 *  An original queue is media list is untouched from a source (ex. folder, playlist, etc.).
 *
 *  A non-original queue is any queue the user has intentionally created or modified. These queues will not be automatically replaced.
 */
class QueueBoard(
    private val player: GramophonePlaybackService,
    queues: MutableList<MultiQueueObject> = ArrayList(),
) {
    private val QUEUE_DEBUG = true // TODO: disable when done
    private val TAG = QueueBoard::class.simpleName.toString()

    val masterQueues: MutableList<MultiQueueObject> = mutableListOf()

    init {
        masterQueues.clear()
        if (!queues.isEmpty()) {
            masterQueues.addAll(queues)
        }
    }

    /**
     * ========================
     * Data structure management
     * ========================
     */

    /**
     * Load this queue to the player, and save the player's queue back to QueueBoard.
     *
     * @param index Index of the queue in [masterQueues]
     * @param startIndex Optional start position override for the queue loaded into the player
     */
    fun commitQueue(
        index: Int,
        startIndex: Int = -1
    ) {
        Log.d(TAG, "commitQueue() called")
        if (index < 0 || index >= masterQueues.size) {
            Log.w(
                TAG,
                "commitQueue() index $index out of bounds (size = ${masterQueues.size}). Aborting"
            )
            return
        }

        var new = masterQueues.removeAt(index)
        if (startIndex != -1) {
            new = new.copy(startIndex = startIndex, startPositionMs = C.TIME_UNSET)
        }
        val plr = player.endedWorkaroundPlayer!!
        if (QUEUE_DEBUG)
            Log.d(
                TAG,
                "Setting current queue; $new; ids: ${plr.currentMediaItem?.mediaId}, ${new.queue[new.startIndex].mediaId}"
            )
        plr.setMediaItems(
            new.queue, new.startIndex,
            new.startPositionMs,
            new.title, new.expiry == null, new.isOriginal, new.shuffleOrder, new.ended,
            new.repeatMode, new.shuffleModeEnabled, null
        )
    }

    /**
     * Pin a queue to the QueueBoard. This queue will no longer be eligible for automatic removal.
     *
     * @param index Queue index.
     */
    fun pinQueue(index: Int): Boolean {
        masterQueues[index].expiry = null
        return true
    }

    /**
     * Unpin a queue from the QueueBoard. This queue will be eligible for automatic removal after
     * the expiry threshold.
     *
     * @param index Queue index.
     * @return true if the operation is successful, otherwise false
     */
    fun unpinQueue(index: Int): Boolean {
        if (masterQueues.isEmpty()) return false
        masterQueues[index].expiry = System.currentTimeMillis() + QUEUE_EXPIRY_MS
        return true
    }

    /**
     * Remove expired queues from the QueueBoard
     */
    fun trimQB() {
        val currentTimeMillis = System.currentTimeMillis()
        val newQueueList = masterQueues.filter {
            it.expiry == null || it.expiry!! > currentTimeMillis
        }
        masterQueues.clear()
        masterQueues.addAll(newQueueList)
    }


    /**
     * Add a new queue to the QueueBoard, or replace an existing queue if it already exists. Queues
     * are determined to be equivalent if [MultiQueueObject.title] is the same and [MultiQueueObject.isOriginal]
     * is false.
     *
     * @param queueId
     * @param title Title of the queue.
     * @param mediaList Media items to add to the queue.
     * @param mediaItemIndex Start index.
     * @param startPositionMs Start position.
     * @param shouldPin Specify a timestamp to indicate when the queue is expires, otherwise null
     *  for a queue that never expires.
     * @param isOriginal false if the user has modified the queue, and this queue is not replaceable.
     * @param repeatMode
     * @param shuffleOrder A shuffle order will enable shuffling of the queue, otherwise null disables it.
     * @param ended
     *
     */
    fun addQueue(
        queueId: Long,
        title: String,
        mediaList: List<MediaItem>,
        mediaItemIndex: Int = 0,
        startPositionMs: Long?,
        shouldPin: Boolean,
        isOriginal: Boolean,
        repeatMode: (@Player.RepeatMode Int)?,
        shuffleOrder: CircularShuffleOrder.Persistent?,
        ended: Boolean,
    ) {
        if (QUEUE_DEBUG)
            Log.d(TAG, "Queue data: $masterQueues")
        if (QUEUE_DEBUG)
            Log.d(
                TAG, "Adding to queue \"$title\". medialist size = ${mediaList.size}. " +
                        "replace/startIndex = $mediaItemIndex"
            )
        if (mediaList.isEmpty()) return //throw IllegalArgumentException("Media list cannot be empty")

        masterQueues.removeAll { it.isOriginal && it.title.trimEnd() == title }

        // (4) add new queue
        if (QUEUE_DEBUG)
            Log.d(TAG, "Adding: (4) new queue")

        val newQueue = MultiQueueObject(
            id = queueId,
            index = -1,
            title = title,
            expiry = if (!shouldPin) System.currentTimeMillis() + QUEUE_EXPIRY_MS else null,
            queue = ArrayList(mediaList),
            startIndex = mediaItemIndex,
            startPositionMs = startPositionMs ?: C.TIME_UNSET,
            repeatMode = repeatMode ?: 0,
            shuffleOrder = shuffleOrder,
            ended = ended,
            isOriginal = isOriginal,
        )

        addQueue(newQueue)
    }

    fun addQueue(mq: MultiQueueObject) {

        if (QUEUE_DEBUG) {
            Log.d(
                TAG, "Adding to queue \"${mq.title}\". medialist size = ${mq.queue.size}. " +
                        "replace/startIndex = ${mq.startIndex}"
            )
        }
        if (mq.queue.isEmpty()) return //throw IllegalArgumentException("Media list cannot be empty")

        masterQueues.removeAll { it.isOriginal && it.title.trimEnd() == mq.title }
        masterQueues.bubbleUp(mq)
    }

    /**
     * Deletes a queue.
     *
     * @param id queueId.
     * @return true if the deletion is successful, otherwise false.
     */
    fun deleteQueue(id: Long): Boolean {
        val mq = masterQueues.find { it.id == id }
        val index = mq?.let {
            masterQueues.indexOf(it)
        }
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE AT INDEX: $index")

        if (index == null) return false

        try {
            masterQueues.removeAt(index)
        } catch (e: IndexOutOfBoundsException) {
            Log.w(TAG, e.message, e)
            return false
        }

        return true
    }

    /**
     * Reorder a queue.
     *
     * @param fromIndex
     * @param toIndex
     */
    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex < toIndex) {
            masterQueues.add(toIndex - 1, masterQueues.removeAt(fromIndex))
        } else {
            masterQueues.add(toIndex, masterQueues.removeAt(fromIndex))
        }
    }

    /**
     * =================
     * Player management
     * =================
     */

    /**
     * Get all copy of all queues
     */
    fun getInactiveQueues() = masterQueues.map {
        it.copy(
            queue = ArrayList(),
            fakeQueueSize = it.getSize(),
            fakeQueueLength = it.getDuration(),
        )
    }


    /**
     * Get a single queue given a queue id.
     */
    fun getInactiveQueue(id: Long): MultiQueueObject? {
        return masterQueues.firstOrNull { it.id == id }?.let {
            it.copy(
                fakeQueueSize = it.getSize(),
                fakeQueueLength = it.getDuration()
            )
        }
    }

    /**
     * Get a single queue given an index.
     */
    fun getInactiveQueue(index: Int): MultiQueueObject? {
        return masterQueues.getOrNull(index)?.let {
            it.copy(
                fakeQueueSize = it.getSize(),
                fakeQueueLength = it.getDuration()
            )
        }
    }

    /**
     *
     */
    fun renameQueue(index: Int, newName: String, dryRun: Boolean): Boolean {
        if (index >= masterQueues.size) return false
        return renameQueue(masterQueues[index], newName, dryRun)
    }

    /**
     * Rename a queue, if possible.
     *
     * Renaming a queue will set [MultiQueueObject.isOriginal] to false.
     *
     * @param mq
     * @param newName
     * @param dryRun Call this function with dryRun = true to test for if this rename action is
     *  allowed, then call with dryRun = false to preceding with it. This prevents ui desync.
     *
     * For the time being, queues cannot have the same title, even if its allowed in the underlying
     * codes. TODO(mq): re-evaluate later
     */
    fun renameQueue(mq: MultiQueueObject, newName: String, dryRun: Boolean): Boolean {
        // If you rename a queue to "Folder1 (+)" and have a non-original queue named "Folder 1", then you will have 2 queues the same name
        val plr = player.endedWorkaroundPlayer!!
        if (plr.currentTitle == newName || masterQueues.any { it.title == newName }) {
            if (QUEUE_DEBUG)
                Log.d(TAG, "Failed to rename queue to \"$newName\". Already exists")
            return false
        }
        val found = masterQueues.any { it == mq }
        if (found) {
            val oldIndex = masterQueues.indexOf(mq)
            if (!dryRun) {
                masterQueues[oldIndex] = masterQueues[oldIndex].copy(
                    title = newName,
                    isOriginal = false
                )
            }
            if (QUEUE_DEBUG)
                Log.d(TAG, "Successfully renamed queue from \"${mq.title}\" to \"$newName\"")
            return true
        } else {
            if (QUEUE_DEBUG)
                Log.d(TAG, "Failed to rename queue. Not found")
            return false
        }
    }

    /**
     * Debug uses. Simulate 2 hours of time passing.
     */
    fun age() {
        masterQueues.forEach {
            if (it.expiry != null) {
                it.expiry = it.expiry!! + 2L * 36000000L
            }
        }
    }

    val context
        get() = player as Context

}

/**
 * Insert (or move) this queue to the last spot in the QueueBoard
 */
private fun MutableList<MultiQueueObject>.bubbleUp(mq: MultiQueueObject) {
    remove(mq)
    add(mq)
    forEachIndexed { index, mq ->
        mq.index = index
    }
}


/**
 * A representation of a queue
 *
 * @param
 * @param title Queue title (and UID)
 * @param queue List of media items
 */
data class MultiQueueObject(
    val id: Long, // queue uid
    var index: Int, // order of queue
    var title: String,
    /**
     * Expiry denotes when this queue is eligible for auto removal; these events happen when
     * triggered by the user, or automatically on QueueBoard initialization, or when the user switches any queue. //TODO: not implemented
     *
     * Active queues will never be automatically removed, however, the pin state does not
     * automatically change. When a pinned queue becomes an active queue, it will remain pinned when
     * it becomes inactive. If said queue is unpinned, then it will renew its expiry time when it
     * becomes inactive.
     */
    var expiry: Long?,
    /**
     * The order of songs are dynamic. This should not be accessed from outside QueueBoard.
     */
    val queue: MutableList<MediaItem>,

    var startIndex: Int = C.INDEX_UNSET, // position of current song
    var startPositionMs: Long = C.TIME_UNSET,
    var repeatMode: @Player.RepeatMode Int = 0,

    var shuffleOrder: CircularShuffleOrder.Persistent? = null,
    var ended: Boolean = false,
    val isOriginal: Boolean = true,

    private var fakeQueueSize: Int? = null,
    private var fakeQueueLength: Long? = null
) {
    override fun toString() =
        "$title ($id) startIndex=$startIndex, startPositionMs=$startPositionMs, repeatMode=$repeatMode, shuffleModeEnabled=$shuffleModeEnabled, ended=$ended, mediaItems_size=${queue.size}, expiry=$expiry"

    val shuffleModeEnabled
        get() = shuffleOrder != null

    /**
     * Retrieve the song at current position in the queue
     */
    fun getCurrentSong(): MediaItem? {
        return queue.getOrNull(startIndex)
    }

    /**
     * Retrieve a song given a song ID. Returns null if no song is found
     */
    fun findSong(mediaId: String): MediaItem? {
        val currentSong = getCurrentSong()
        if (currentSong?.mediaId == mediaId) {
            return currentSong
        }

        return queue.fastFirstOrNull { it.mediaId == mediaId }
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in milliseconds
     */
    fun getDuration(): Long {
        return fakeQueueLength ?: queue.sumOf {
            it.mediaMetadata.durationMs ?: 0L
        }
    }

    /**
     * Get the length of the queue
     */
    fun getSize() = fakeQueueSize ?: queue.size

    fun clearFakeStats() {
        fakeQueueSize = null
        fakeQueueLength = null
    }

    fun toBundle(): Bundle =
        Bundle().apply {
            val binder = MediaItemList(queue)

            putLong("id", id)
            putInt("index", index)
            putString("title", title)
            putString("expiry", expiry.toString())

            putBinder("queue", binder)

            putInt("startIndex", startIndex)
            putLong("startPositionMs", startPositionMs)
            putInt("repeatMode", repeatMode)

            putBoolean("ended", ended)
            putBoolean("isOriginal", isOriginal)
            putParcelable("shuffleOrder", shuffleOrder)

            fakeQueueSize?.let {
                putInt("fakeQueueSize", it)
            }
            fakeQueueLength?.let {
                putLong("fakeQueueLength", it)
            }
        }

    companion object {
        fun fromBundle(bundle: Bundle): MultiQueueObject {
            val binder = bundle.getBinder("queue")!!
            val queue = MediaItemList.getList(binder).toMutableList()
//            val epochMillis = bundle.getLong("expiry")
            return MultiQueueObject(
                id = bundle.getLong("id"),
                index = bundle.getInt("index"),
                title = bundle.getString("title") ?: "",
                expiry = bundle.getString("expiry")?.toLongOrNull(),
                queue = queue,

                startIndex = bundle.getInt("startIndex", C.INDEX_UNSET),
                startPositionMs = bundle.getLong("startPositionMs", C.TIME_UNSET),
                repeatMode = bundle.getInt("repeatMode", REPEAT_MODE_OFF),
                ended = bundle.getBoolean("ended"),
                isOriginal = bundle.getBoolean("isOriginal"),
                shuffleOrder = BundleCompat.getParcelable(
                    bundle, "shuffleOrder",
                    CircularShuffleOrder.Persistent::class.java
                ),

                fakeQueueSize = bundle.getInt("fakeQueueSize", C.INDEX_UNSET)
                    .let { if (it == C.INDEX_UNSET) null else it },
                fakeQueueLength = bundle.getLong("fakeQueueLength", C.TIME_UNSET)
                    .let { if (it == C.TIME_UNSET) null else it }
            )
        }
    }
}

class MultiQueueList(val list: List<MultiQueueObject>) : Binder() {
    private val blr by lazy { BundleListRetriever(list.map { it.toBundle() }) }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == FIRST_CALL_TRANSACTION) {
            return blr.transact(code, data, reply, flags)
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        fun getList(binder: IBinder?): List<MultiQueueObject> {
            if (binder == null) return emptyList()
            if (binder is MultiQueueList) {
                return binder.list
            }
            return BundleListRetriever.getList(binder).map { MultiQueueObject.fromBundle(it) }
        }
    }
}