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
import kotlinx.coroutines.flow.MutableStateFlow
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.MediaItemList

private const val QUEUE_EXPIRY_MS = 10 * 36000000 // 10 hrs

/**
 * Multiple queues manager.
 *
 * Queues are ordered most recent modification,
 */
class QueueBoard(
    private val player: GramophonePlaybackService,
    val masterQueues: MutableList<MultiQueueObject> = mutableListOf(),
    queues: MutableList<MultiQueueObject> = ArrayList(),
) {
    private val QUEUE_DEBUG = true // TODO: disable when done
    private val TAG = QueueBoard::class.simpleName.toString()

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
     * Push this queue to the player, and save the player queue back to QueueBoard.
     *
     * @param index
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
     * Pin a queue.
     *
     * @param index Queue index.
     */
    fun pinQueue(index: Int): Boolean {
        masterQueues[index].expiry = null
        return true
    }

    /**
     * Unpin a queue.
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
     * Add a new queue to the QueueBoard, or add to a queue if it exists.
     *
     * Depending on the state of the QueueBoard and player, this result in differing behaviour:
     *
     * Queue already exists:
     * 1. Contents (by songID) are a perfect match: Update metadata (currentMediaItemIndex, shuffle
     *      order).
     * 2. Contents are different and given "isOriginal" flag: Update metadata, replace all existing
     *      queue content with new content.
     * 3. Contents are different: Update metadata, add all new content to the end of the old content.
     *      Queue title gets a "+" suffix if not already present.
     *
     * Queue does not exist:
     * 4. Queue is added as a new queue.
     *
     *
     * @param title Title (effective uid) of the queue.
     * @param mediaList Media items to add to the queue.
     * @param player
     * @param shuffled media3 isShuffleEnabled
     * @param mediaItemIndex media3 startIndex
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

        masterQueues.bubbleUp(newQueue)
    }

    /**
     * Deletes a queue.
     *
     * When deleting the active queue, the last inactive queue is loaded. When the active queue is
     * the only queue, playback is stopped.
     *
     * @param index
     * @return true if the deletion is successful, otherwise false.
     */
    fun deleteQueue(index: Int): Boolean {
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE AT INDEX: $index")

        try {
            masterQueues.removeAt(index)
        } catch (e: IndexOutOfBoundsException) {
            Log.w(TAG, e.message.toString(), e)
            return false
        }

        return true
    }

    /**
     * Deletes a queue.
     *
     * When deleting the active queue, the last inactive queue is loaded. When the active queue is
     * the only queue, playback is stopped.
     *
     * @param id
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
     * Move a queue in masterQueues
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
     * Get a single queue (or several queues in the future)
     */
    fun getQueue(index: Int): List<MultiQueueObject> {
        return listOfNotNull(
            masterQueues.getOrNull(index)?.let {
                it.copy(
                    fakeQueueSize = it.getSize(),
                    fakeQueueLength = it.getDuration()
                )
            }
        )
    }

    fun renameQueue(index: Int, newName: String, dryRun: Boolean): Boolean {
        if (index >= masterQueues.size) return false
        return renameQueue(masterQueues[index], newName, dryRun)
    }

    fun renameQueue(mq: MultiQueueObject, newName: String, dryRun: Boolean): Boolean {
        // TODO(MQ) how to handle isOriginal and i18n bs.
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
                    isOriginal = true
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
     * Debug uses
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
 * Insert (or move) this queue to the last spot.
 */
private fun MutableList<MultiQueueObject>.bubbleUp(mq: MultiQueueObject) {
    remove(mq)
    add(mq)
    forEachIndexed { index, mq ->
        mq.index = index
    }
}


/**
 * @param title Queue title (and UID)
 * @param queue List of media items
 */
data class MultiQueueObject(
    val id: Long, // queue uid
    var index: Int, // order of queue
    var title: String,
    /**
     * Expiry denotes when this queue is eligible for auto removal; these events happen when
     * triggered by the user, or automatically on QueueBoard initialization, or when the user switches any queue.
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