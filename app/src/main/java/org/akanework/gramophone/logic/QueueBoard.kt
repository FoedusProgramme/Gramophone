package org.akanework.gramophone.logic

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import kotlin.math.exp
import kotlin.random.Random

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
    private val QUEUE_DEBUG = true
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
     * Push this queue to the player, and save the player queue back to QueueBoard
     *
     * @param mq
     */
    fun commitQueue(
        mq: MultiQueueObject,
        setMediaItems: Boolean = true,
        shouldResume: Boolean = true
    ) =
        commitQueue(
            index = masterQueues.indexOf(mq),
            setMediaItems = setMediaItems,
            shouldResume = shouldResume,
        )

    /**
     * Push this queue to the player, and save the player queue back to QueueBoard. The last queue
     * is assumed to be the active queue, and second last is assumed to be the queue to load.
     *
     * @param index
     */
    fun commitQueue(
        index: Int,
        startIndex: Int = -1,
        setMediaItems: Boolean = true,
        shouldResume: Boolean = true,
        saveLast: Boolean = true
    ) {
        Log.v(TAG, "commitQueue() called")
        if (index < 0 || index >= masterQueues.size) {
            Log.w(
                TAG,
                "commitQueue() index $index out of bounds (size = ${masterQueues.size}). Aborting"
            )
            return
        }

        // assume last == active queue, second last == to load. No save when no active queue
        if (saveLast) {
            val old = masterQueues.lastIndex
            if (masterQueues.size > 1 && old >= 0) {
                syncQueueFromPlayer(masterQueues[old])
            }
        }

        var new = masterQueues[index]
        masterQueues.remove(new)
        if (startIndex != -1) {
            new = new.copy(startIndex = startIndex, startPositionMs = C.TIME_UNSET)
        }
        masterQueues.add(new)
        if (setMediaItems) {
            setCurrQueue(new, true, shouldResume)
        }
    }

    /**
     * Pin a queue.
     *
     * @param index Queue index. If an index of -1 is provided, the active queue is pinned.
     * @return true if the operation is successful, otherwise false
     */
    fun pinQueue(index: Int): Boolean {
        if (masterQueues.isEmpty()) return false
        if (index == -1) {
            masterQueues[masterQueues.lastIndex].expiry.value = null
            return true
        }
        masterQueues[index].expiry.value = null
        return true
    }

    /**
     * Unpin a queue.
     *
     * @param index Queue index. If an index of -1 is provided, the active queue is unpinned.
     * @return true if the operation is successful, otherwise false
     */
    fun unpinQueue(index: Int): Long {
        if (masterQueues.isEmpty()) return -1L
        val expiry = System.currentTimeMillis() + QUEUE_EXPIRY_MS
        if (index == -1) {
            masterQueues[masterQueues.lastIndex].expiry.value =
                System.currentTimeMillis() + QUEUE_EXPIRY_MS
            return expiry
        }
        masterQueues[index].expiry.value = System.currentTimeMillis() + QUEUE_EXPIRY_MS
        return expiry
    }

    /**
     * Remove expired queues from the QueueBoard
     */
    fun trimQB() {
        val currentTimeMillis = System.currentTimeMillis()
        val newQueueList = masterQueues.filter {
            it.expiry.value == null || it.expiry.value!! > currentTimeMillis
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
     * @param isOriginal Specifies if the queue is an original copy of a library media list (ex.
     *      folder, search results, playlist, etc.). Original copies will sync existing queue's media
     *      items with the provided media items. "Un-original" queues will append media items to the
     *      end of the queue, or create a new queue if an existing queue does not exist.
     *
     */
    fun addQueue(
        title: String,
        mediaList: List<MediaItem>,
        mediaItemIndex: Int = 0,
        startPositionMs: Long?,
        isOriginal: Boolean = true,
        shouldPin: Boolean = false,
    ): MultiQueueObject {
        if (QUEUE_DEBUG)
            Log.d(TAG, "Queue data: $masterQueues")
        if (QUEUE_DEBUG)
            Log.d(
                TAG, "Adding to queue \"$title\". medialist size = ${mediaList.size}. " +
                        "replace/startIndex = $isOriginal/$mediaItemIndex"
            )

        // look for matching queue. Title is (effectively) uid
        val match = masterQueues.firstOrNull { it.title.trimEnd() == title }

        if (match != null) {
            val containsAll =
                mediaList.size == match.getSize() && mediaList.all { s ->
                    match.queue.any { s.mediaId == it.mediaId }
                }
            if (containsAll) {
                // (1) perfect match
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding: (1) perfect match")
            } else if (isOriginal) {
                // (2) replace all in queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding: (2) perfect match")

                match.queue.clear()
                match.queue.addAll(mediaList)
            } else {
                // (3) add all to end of the queue. Create extension queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding: (3) perfect match")

                match.queue.addAll(mediaList)

                // Titles ending in "+​" aka \u200B signify a extension queue
                // Original copies will transion into an extention queue when media items are added
                if (!match.title.endsWith("(+\u200B)")) {
                    match.title = match.title + "(+\u200B)"
                }
            }

            match.startIndex = mediaItemIndex
            startPositionMs?.let {
                match.startPositionMs = it
            }

            masterQueues.bubbleUp(match)
            return match
        } else {
            // (4) add new queue
            if (QUEUE_DEBUG)
                Log.d(TAG, "Adding: (4) new queue")

            val newQueue = MultiQueueObject(
                id = Random.nextLong(),
                index = -1,
                title = title,
                expiry = MutableStateFlow(if (!shouldPin) System.currentTimeMillis() + QUEUE_EXPIRY_MS else null),
                queue = ArrayList(mediaList),
                startIndex = mediaItemIndex,
                startPositionMs = startPositionMs ?: C.TIME_UNSET,
                repeatMode = player.endedWorkaroundPlayer!!.repeatMode,
                shuffleOrder = null,
                ended = false,
            )

            masterQueues.bubbleUp(newQueue)
            return newQueue
        }
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
            if (index == masterQueues.lastIndex) {
                masterQueues.removeAt(index)
                if (index <= 0) {
                    player.endedWorkaroundPlayer?.removeMediaItems(0, Int.MAX_VALUE)
                } else {
                    commitQueue(index - 1, setMediaItems = false)
                }
            } else if (index <= masterQueues.lastIndex - 1) {
                masterQueues.removeAt(index)
            } else {
                throw IndexOutOfBoundsException("Index of queue $index to delete OOB of 0-${masterQueues.size - 1}")
            }
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
     *
     * @return New current position tracker
     */
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex == masterQueues.lastIndex || toIndex == masterQueues.lastIndex) {
            return false
        }

        if (fromIndex < toIndex) {
            masterQueues.add(toIndex - 1, masterQueues.removeAt(fromIndex))
        } else {
            masterQueues.add(toIndex, masterQueues.removeAt(fromIndex))
        }
        return true
    }

    /**
     * =================
     * Player management
     * =================
     */

    /**
     * Get all copy of all queues
     */
    fun getInactiveQueues() = masterQueues.dropLast(1).map {
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
        val plr = player.endedWorkaroundPlayer!!
        var duration = 0L
        for (i in 0 until plr.mediaItemCount) {
            duration += plr.getMediaItemAt(i).mediaMetadata.durationMs
                ?: 0L
        }

        return if (index == C.INDEX_UNSET || index == masterQueues.lastIndex) {
            listOfNotNull(
                masterQueues.lastOrNull()?.copy(
                    fakeQueueSize = plr.mediaItemCount,
                    fakeQueueLength = duration
                )
            )
        } else {
            listOfNotNull(
                masterQueues.getOrNull(index)?.let {
                    it.copy(
                        fakeQueueSize = it.getSize(),
                        fakeQueueLength = it.getDuration()
                    )
                }
            )
        }
    }


    fun renameQueue(mq: MultiQueueObject, newName: String): Boolean {
        if (masterQueues.any { it.title == newName }) {
            if (QUEUE_DEBUG)
                Log.d(TAG, "Failed to rename queue to \"$newName\". Already exists")
            return false
        }
        val found = masterQueues.any { it == mq }
        if (found) {
            val oldIndex = masterQueues.indexOf(mq)
            val q = masterQueues.removeAt(oldIndex)
            masterQueues.add(oldIndex, q.copy(title = newName))

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
     * Load a queue into the media player. This should ran exclusively on the main thread.
     *
     * @param mq Queue object
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @return New current position tracker
     */
    // TODO: OuterTune hacks around shuffleModeEnabled by replacing all media items in the queue when shuffleModeEnabled changes,
    //  so setCurrQueue was created to allows for seamless transitions. The side effect is that seamless transitions were also
    //  extendable to *any* queue change. In theory, this should work for Gramophone too, but I have not tested it at all
    private fun setCurrQueue(
        mq: MultiQueueObject?,
        seamlessAllowed: Boolean,
        shouldResume: Boolean
    ): Int? {
        Log.d(
            TAG,
            "Loading queue ${mq?.title ?: "null"} into player. Shuffle state = ${mq?.shuffleModeEnabled}"
        )

        val plr = player.endedWorkaroundPlayer!!

        if (mq == null || mq.queue.isEmpty()) {
            plr.realSetMediaItems(ArrayList(), C.INDEX_UNSET, C.TIME_UNSET)
            return null
        }

        val startIndex = mq.startIndex

        val mediaItems: MutableList<MediaItem> = mq.queue

        Log.d(
            TAG,
            "Setting current queue; $mq; ids: ${plr.currentMediaItem?.mediaId}, ${mediaItems[startIndex].mediaId}"
        )

        val seed = try {
            CircularShuffleOrder.Persistent.deserialize(mq.shuffleOrder)
        } catch (e: Exception) {
            plr.nextShuffleOrder = null
            throw e
        }

        /**
         * current playing == jump target, do seamlessly
         */
        val seamlessSupported = seamlessAllowed && (startIndex < mediaItems.size)
                && plr.currentMediaItem?.mediaId == mediaItems[startIndex].mediaId
        if (seamlessSupported) {
            Log.d(TAG, "Trying seamless queue switch. Is first song?: ${startIndex == 0}")
            val playerIndex = plr.currentMediaItemIndex

            plr.replaceMediaItem(
                playerIndex,
                mediaItems[playerIndex]
            ) // update current's metadata
            if (startIndex == 0) {
                // remove all songs before the currently playing one and then replace all the items after
                if (playerIndex > 0) {
                    plr.removeMediaItems(0, playerIndex)
                }
                plr.replaceMediaItems(1, Int.MAX_VALUE, mediaItems.drop(1))
            } else {
                // replace items up to current playing, then replace items after current
                plr.replaceMediaItems(
                    0, playerIndex,
                    mediaItems.subList(0, startIndex)
                )
                plr.replaceMediaItems(
                    startIndex + 1, Int.MAX_VALUE,
                    mediaItems.subList(startIndex + 1, mediaItems.size)
                )
            }

            plr.exoPlayer.setShuffleOrder(seed.toFactory()(mq.startIndex, mq.getSize(), plr))
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")

            if (plr.nextShuffleOrder != null)
                throw IllegalStateException("shuffleFactory was found orphaned")

            plr.nextShuffleOrder = seed.toFactory()

            plr.realSetMediaItems(
                mediaItems, startIndex,
                if (shouldResume) mq.startPositionMs else C.TIME_UNSET
            )
            if (plr.nextShuffleOrder != null)
                throw IllegalStateException("shuffleFactory was not consumed during restore")
        }

        plr.shuffleModeEnabled = mq.shuffleModeEnabled
        plr.repeatMode = mq.repeatMode

        return startIndex
    }


    /**
     * =================
     * Util
     * =================
     */


    private fun dumpPlaylist(): MutableList<MediaItem> {
        val items = ArrayList<MediaItem>()
        val instance = player.endedWorkaroundPlayer!!
        for (i in 0 until instance.mediaItemCount) {
            items.add(instance.getMediaItemAt(i))
        }

        return items
    }

    /**
     * Update the queue in QueueBoard with player attributes
     */
    private fun syncQueueFromPlayer(mq: MultiQueueObject) {
        val plr = player.endedWorkaroundPlayer!!
        mq.startIndex = plr.currentMediaItemIndex
        mq.startPositionMs = plr.currentPosition
        mq.repeatMode = plr.repeatMode
        val persistent = if (plr.shuffleModeEnabled) {
            CircularShuffleOrder.Persistent(plr.exoPlayer.shuffleOrder as CircularShuffleOrder)
        } else {
            null
        }
        mq.shuffleOrder = persistent?.toString()
        mq.queue.clear()
        mq.queue.addAll(dumpPlaylist())
        mq.clearFakeStats()
    }

    /**
     * Debug uses
     */
    fun age() {
        masterQueues.forEach {
            if (it.expiry.value != null) {
                it.expiry.value = it.expiry.value!! + 2L * 36000000L
            }
        }
    }

    val context
        get() = player as Context

}

/**
 * Move this queue to the last non-active spot. If there are no queues, this queue gets added to the
 * active slot
 */
private fun MutableList<MultiQueueObject>.bubbleUp(mq: MultiQueueObject) {
    if (!isEmpty()) {
        remove(mq)
        if (lastIndex >= 0) {
            add(lastIndex, mq)
        }
    } else {
        add(mq)
    }
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
    var expiry: MutableStateFlow<Long?>,
    /**
     * The order of songs are dynamic. This should not be accessed from outside QueueBoard.
     */
    val queue: MutableList<MediaItem>,

    var startIndex: Int = C.INDEX_UNSET, // position of current song
    var startPositionMs: Long = C.TIME_UNSET,
    var repeatMode: Int = 0,

    var shuffleOrder: String? = null,
    // TODO: why did i need this again?
    var ended: Boolean = false,

    private var fakeQueueSize: Int? = null,
    private var fakeQueueLength: Long? = null
) {
    override fun toString() =
        "$title ($id) startIndex=$startIndex, startPositionMs=$startPositionMs, repeatMode=$repeatMode, shuffleModeEnabled=$shuffleModeEnabled, ended=$ended, mediaItems_size=${queue.size}, expiry=${expiry.value}"

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
//            val binder = BundleListRetriever(queue.map { it.toBundle() })

            putLong("id", id)
            putInt("index", index)
            putString("title", title)
            putString("expiry", expiry.value?.toString())

//            putBinder("queue", binder)
            putParcelableArrayList("queue", ArrayList<Parcelable>(queue.map { it.toBundle() }))

            putInt("startIndex", startIndex)
            putLong("startPositionMs", startPositionMs)
            putInt("repeatMode", repeatMode)
            putBoolean("shuffleModeEnabled", shuffleModeEnabled)
            putBoolean("ended", ended)
            putString("shuffleOrder", shuffleOrder)

            fakeQueueSize?.let {
                putInt("fakeQueueSize", it)
            }
            fakeQueueLength?.let {
                putLong("fakeQueueLength", it)
            }
        }

    companion object {
        fun fromBundle(bundle: Bundle): MultiQueueObject {
//            val binder = bundle.getBinder("queue")!!
//            val queue = BundleListRetriever.getList(binder).map { MediaItem.fromBundle(it) }
//                .toMutableList()
//            val epochMillis = bundle.getLong("expiry")
            return MultiQueueObject(
                id = bundle.getLong("id"),
                index = bundle.getInt("index"),
                title = bundle.getString("title") ?: "",
                expiry = MutableStateFlow(bundle.getString("expiry")?.toLongOrNull()),
//                queue = queue,
                queue = (bundle.getParcelableArrayList<Bundle>("queue")
                    ?: emptyList()).map { MediaItem.fromBundle(it) }.toMutableList(),

                startIndex = bundle.getInt("startIndex", C.INDEX_UNSET),
                startPositionMs = bundle.getLong("startPositionMs", C.TIME_UNSET),
                repeatMode = bundle.getInt("repeatMode", REPEAT_MODE_OFF),
                ended = bundle.getBoolean("ended"),
                shuffleOrder = bundle.getString("shuffleOrder"),

                fakeQueueSize = bundle.getInt("fakeQueueSize", C.INDEX_UNSET)
                    .let { if (it == C.INDEX_UNSET) null else it },
                fakeQueueLength = bundle.getLong("fakeQueueLength", C.TIME_UNSET)
                    .let { if (it == C.TIME_UNSET) null else it }
            )
        }
    }
}