package org.akanework.gramophone.logic

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


/**
 * Multiple queues manager.
 *
 * Queues are ordered most recent modification,
 */
class QueueBoard(
    private val player: GramophonePlaybackService,
    val masterQueues: MutableList<MultiQueueObject> = mutableListOf(),
    queues: MutableList<MultiQueueObject> = ArrayList(),
    private var maxQueues: Int = 20
) {
    private val QUEUE_DEBUG = true
    private val TAG = QueueBoard::class.simpleName.toString()

    init {
        masterQueues.clear()
        if (maxQueues < 0) {
            maxQueues = 1
        }
        if (!queues.isEmpty()) {
            masterQueues.addAll(
                queues.subList(
                    (queues.size - maxQueues).coerceAtLeast(0),
                    queues.size
                )
            )
        }

        // todo: remove when figure out persist and load
        masterQueues.add(
            MultiQueueObject(
                id = Random.nextLong(),
                title = "[Existing queue]",
                queue = ArrayList(),
                startIndex = C.INDEX_UNSET,
                startPositionMs = C.TIME_UNSET,
                repeatMode = REPEAT_MODE_OFF,
                shuffleModeEnabled = false,
                shuffleOrder = null,
                ended = false,
            )
        )
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
    fun commitQueue(mq: MultiQueueObject, shouldResume: Boolean = true) =
        commitQueue(masterQueues.indexOf(mq), shouldResume)

    /**
     * Push this queue to the player, and save the player queue back to QueueBoard. The last queue
     * is assumed to be the active queue, and second last is assumed to be the queue to load.
     *
     * @param index
     */
    fun commitQueue(index: Int, shouldResume: Boolean = true) {
        if (index < 0 || index >= masterQueues.size) {
            Log.w(TAG, "commitQueue() index out of bounds. Aborting")
            return
        }

        // assume last == active queue, second last == to load. No save when no active queue
        val old = masterQueues.lastIndex
        if (masterQueues.size > 1 && old >= 0) {
            syncQueueFromPlayer(masterQueues[old])
        }

        val new = masterQueues[index]
        masterQueues.remove(new)
        masterQueues.add(new)
        setCurrQueue(new, false, shouldResume)
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
        isOriginal: Boolean = false,
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

                match.startIndex = mediaItemIndex

                masterQueues.bubbleUp(match)
                return match
            } else if (isOriginal) {
                // (2) replace all in queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding: (2) perfect match")

                match.startIndex = mediaItemIndex
                match.queue.clear()
                match.queue.addAll(mediaList)

                masterQueues.bubbleUp(match)
                return match
            } else {
                // (3) add all to end of the queue. Create extension queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding: (3) perfect match")

                match.startIndex = mediaItemIndex
                match.queue.addAll(mediaList)

                // Titles ending in "+​" aka \u200B signify a extension queue
                // Original copies will transion into an extention queue when media items are added
                if (!match.title.endsWith("(+\u200B)")) {
                    match.title = match.title + "(+\u200B)"
                }

                masterQueues.bubbleUp(match)
                return match
            }
        } else {
            // (4) add new queue
            if (QUEUE_DEBUG)
                Log.d(TAG, "Adding: (4) new queue")
            if (masterQueues.size >= maxQueues) {
                deleteQueue(masterQueues.first())
            }

            val newQueue = MultiQueueObject(
                id = Random.nextLong(),
                title = title,
                queue = ArrayList(mediaList),
                startIndex = mediaItemIndex,
                startPositionMs = C.TIME_UNSET,
                repeatMode = player.endedWorkaroundPlayer!!.repeatMode,
                shuffleModeEnabled = false,
                shuffleOrder = null,
                ended = false,
            )

            masterQueues.bubbleUp(newQueue)
            return newQueue
        }
    }

    /**
     * Deletes a queue
     *
     * @param mq
     */
    fun deleteQueue(mq: MultiQueueObject): Int {
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE ${mq.title}")

        val match = masterQueues.firstOrNull { it.title == mq.title }
        if (match != null) {
            masterQueues.remove(match)
        } else {
            Log.w(TAG, "Cannot find queue to delete: ${mq.title}")
        }

        return masterQueues.size
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
    fun getAllQueues() = masterQueues.dropLast(1)


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
            plr.setMediaItems(ArrayList())
            return null
        }

        // I have no idea why this value gets reset to 0 by the end... but ig this works
        val startPositionMs = if (shouldResume) mq.startPositionMs else C.TIME_UNSET
        val startIndex = mq.startIndex

        val mediaItems: MutableList<MediaItem> = mq.queue

        Log.d(
            TAG,
            "Setting current queue; $mq; ids: ${plr.currentMediaItem?.mediaId}, ${mediaItems[startIndex].mediaId}"
        )
        /**
         * current playing == jump target, do seamlessly
         */
        val seamlessSupported = seamlessAllowed && (startIndex < mediaItems.size)
                && plr.currentMediaItem?.mediaId == mediaItems[startIndex].mediaId
        if (seamlessSupported) {
            Log.d(TAG, "Trying seamless queue switch. Is first song?: ${startIndex == 0}")
            val playerIndex = plr.currentMediaItemIndex

            if (startIndex == 0) {
                val playerItemCount = plr.mediaItemCount
                // player.player.replaceMediaItems seems to stop playback so we
                // remove all songs except the currently playing one and then add the list of new items
                if (playerIndex < playerItemCount - 1) {
                    plr.removeMediaItems(
                        playerIndex + 1,
                        playerItemCount
                    )
                }
                if (playerIndex > 0) {
                    plr.removeMediaItems(0, playerIndex)
                }
                // add all songs except the first one since it is already present and playing
                plr.addMediaItems(mediaItems.drop(1))
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
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")
            plr.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        if (plr.shuffleModeEnabled != mq.shuffleModeEnabled) {
            if (plr.shuffleModeEnabled && mq.shuffleOrder == null) {
                Log.w(TAG, "Shuffle mode is enabled but no shuffle order is provided")
            }
            plr.shuffleModeEnabled = mq.shuffleModeEnabled
            mq.shuffleOrder?.let {
                if (it != plr.exoPlayer.shuffleOrder) {
                    plr.exoPlayer.setShuffleOrder(it)
                }
            }
        }
        if (plr.repeatMode != mq.repeatMode) {
            plr.repeatMode = mq.repeatMode
        }

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

    private fun syncQueueFromPlayer(mq: MultiQueueObject) {
        val plr = player.endedWorkaroundPlayer!!
        mq.startIndex = plr.currentMediaItemIndex
        mq.startPositionMs = plr.currentPosition
        mq.repeatMode = plr.repeatMode
        mq.shuffleModeEnabled = plr.shuffleModeEnabled
        mq.shuffleOrder = plr.exoPlayer.shuffleOrder as CircularShuffleOrder
        mq.queue.clear()
        mq.queue.addAll(dumpPlaylist())
    }

}

/**
 * Move this queue to the last non-active spot
 */
private fun MutableList<MultiQueueObject>.bubbleUp(mq: MultiQueueObject) {
    remove(mq)
    if (lastIndex >= 0) {
        add(lastIndex, mq)
    }
}


/**
 * @param title Queue title (and UID)
 * @param queue List of media items
 */
data class MultiQueueObject(
    val id: Long,
//    var index: Int, // order of queue if saved to database
    var title: String,
    /**
     * The order of songs are dynamic. This should not be accessed from outside QueueBoard.
     */
    val queue: MutableList<MediaItem>,

    var startIndex: Int = C.INDEX_UNSET, // position of current song
    var startPositionMs: Long = C.TIME_UNSET,
    var repeatMode: Int = 0,
    var shuffleModeEnabled: Boolean = false,

    var shuffleOrder: CircularShuffleOrder? = null,
    var ended: Boolean = false,
) {
    override fun toString() =
        "$title ($id) startIndex=$startIndex, startPositionMs=$startPositionMs, repeatMode=$repeatMode, shuffleModeEnabled=$shuffleModeEnabled, ended=$ended, mediaItems_size=${queue.size}"


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

    fun setCurrentQueuePos(index: Int) {
        // TODO: uhhh figure out shffle
        startIndex = index
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in seconds
     */
    fun getDuration(): Int {
        return queue.fastSumBy {
            ((it.mediaMetadata.durationMs ?: 0L) / 1000).toInt() // seconds
        }
    }

    /**
     * Get the length of the queue
     */
    fun getSize() = queue.size


    fun toBundle(): Bundle =
        Bundle().apply {
//            val binder = BundleListRetriever(queue.map { it.toBundle() })

            putLong("id", id)
            putString("title", title)

//            putBinder("queue", binder)
            putParcelableArrayList("queue", ArrayList<Parcelable>(queue.map { it.toBundle() }))

            putInt("startIndex", startIndex)
            putLong("startPositionMs", startPositionMs)
            putInt("repeatMode", repeatMode)
            putBoolean("shuffleModeEnabled", shuffleModeEnabled)
            putBoolean("ended", ended)

//              TODO:  shuffleOrder 
        }

    companion object {
        fun fromBundle(bundle: Bundle): MultiQueueObject {
//            val binder = bundle.getBinder("queue")!!
//            val queue = BundleListRetriever.getList(binder).map { MediaItem.fromBundle(it) }
//                .toMutableList()

            return MultiQueueObject(
                id = bundle.getLong("id"),
                title = bundle.getString("title") ?: "",

//                queue = queue,
                queue = (bundle.getParcelableArrayList<Bundle>("queue")
                    ?: emptyList()).map { MediaItem.fromBundle(it) }.toMutableList(),

                startIndex = bundle.getInt("startIndex", C.INDEX_UNSET),
                startPositionMs = bundle.getLong("startPositionMs", C.TIME_UNSET),
                repeatMode = bundle.getInt("repeatMode", REPEAT_MODE_OFF),
                shuffleModeEnabled = bundle.getBoolean("shuffleModeEnabled"),
                ended = bundle.getBoolean("ended"),

//              TODO:  shuffleOrder =
            )
        }
    }
}