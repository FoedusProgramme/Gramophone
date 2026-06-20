package org.akanework.gramophone.logic.utils

import android.os.Parcelable
import androidx.media3.common.C
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlinx.parcelize.Parcelize
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import kotlin.random.Random

/**
 * This shuffle order will take "firstIndex" as first song and play all songs after it.
 */
class CircularShuffleOrder private constructor(
    private val listener: EndedWorkaroundPlayer,
    private val shuffled: IntArray,
    private val random: Random
) : ShuffleOrder {
    private val indexInShuffled = IntArray(shuffled.size)

    init {
        for (i in shuffled.indices) {
            indexInShuffled[shuffled[i]] = i
        }
    }

    companion object {
        private const val TAG = "GramophoneShuffleOrder"
        private fun calculateShuffledList(offset: Int, length: Int, random: Random): IntArray {
            val shuffled = IntArray(length)
            var swapIndex: Int
            for (i in shuffled.indices) {
                swapIndex = random.nextInt(i + 1)
                shuffled[i] = shuffled[swapIndex]
                shuffled[swapIndex] = offset + i
            }
            return shuffled
        }

        private fun calculateListWithFirstIndex(shuffled: IntArray, firstIndex: Int): IntArray {
            if (shuffled.isEmpty() && firstIndex == 0) return shuffled
            if (shuffled.size <= firstIndex) throw IllegalArgumentException("${shuffled.size} <= $firstIndex")
            val fi = shuffled.indexOf(firstIndex)
            val before = shuffled.slice(0..<fi)
            val inclAndAfter = shuffled.slice(fi..<shuffled.size)
            return (inclAndAfter + before).toIntArray()
        }
    }

    constructor(
        listener: EndedWorkaroundPlayer,
        firstIndex: Int,
        length: Int,
        randomSeed: Long
    ) :
            this(listener, firstIndex, length, Random(randomSeed))

    private constructor(
        listener: EndedWorkaroundPlayer,
        firstIndex: Int,
        length: Int,
        random: Random
    ) :
            this(
                listener,
                calculateListWithFirstIndex(calculateShuffledList(0, length, random), firstIndex),
                random
            )

    constructor(
        listener: EndedWorkaroundPlayer,
        shuffledIndices: IntArray,
        randomSeed: Long
    ) :
            this(listener, shuffledIndices.copyOf(), Random(randomSeed))

    override fun getLength(): Int {
        return shuffled.size
    }

    override fun getNextIndex(index: Int): Int {
        val shuffledIndex = indexInShuffled[index] + 1
        return if (shuffledIndex < shuffled.size) shuffled[shuffledIndex] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val shuffledIndex = indexInShuffled[index] - 1
        return if (shuffledIndex >= 0) shuffled[shuffledIndex] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int {
        return if (shuffled.isNotEmpty()) shuffled[shuffled.size - 1] else C.INDEX_UNSET
    }

    override fun getFirstIndex(): Int {
        return if (shuffled.isNotEmpty()) shuffled[0] else C.INDEX_UNSET
    }

    // This shuffles the inserted items among themselves and then adds them after
    // the previous index into shuffled - so if song A is playing and we add three songs B, C and D,
    // B,C,D will be shuffled among themselves to ie D,B,C and then this list will be inserted after
    // A so that song list will now be A,D,B,C,...
    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        listener.nextShuffleOrder?.let { factory ->
            listener.nextShuffleOrder = null
            val nextShuffleOrder = factory(insertionIndex, shuffled.size + insertionCount, listener)
            if (nextShuffleOrder.length != shuffled.size + insertionCount)
                throw IllegalStateException(
                    "next shuffle order size ${nextShuffleOrder.length} " +
                            "does not match requested ${shuffled.size + insertionCount}"
                )
                    .also { Log.e(TAG, Log.getThrowableString(it)!!) }
            return nextShuffleOrder
        }
        // the original list: [0, 1, 2] shuffled: [2, 0, 1] indexInShuffled: [1, 2, 0]
        // insertionIndex for adding after 1 would be 2, 2 is at index 0 in shuffled list, after 0
        // would be 1 so we want to insert into shuffled at index 1 here.
        // If insertionIndex is 0, just add it to the very beginning.
        val insertionPoint = if (insertionIndex > 0) indexInShuffled[insertionIndex - 1] + 1 else 0
        val insertionValues = calculateShuffledList(insertionIndex, insertionCount, random)
        val newShuffled = IntArray(shuffled.size + insertionCount)
        var indexInInsertionList = 0
        var indexInOldShuffled = 0

        for (i in 0 until shuffled.size + insertionCount) {
            if (indexInInsertionList < insertionCount && indexInOldShuffled == insertionPoint) {
                newShuffled[i] = insertionValues[indexInInsertionList++]
            } else {
                newShuffled[i] = shuffled[indexInOldShuffled++]
                if (newShuffled[i] >= insertionIndex) {
                    newShuffled[i] += insertionCount
                }
            }
        }

        return CircularShuffleOrder(listener, newShuffled, Random(random.nextLong()))
    }

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder {
        if (listener.nextShuffleOrder == null && startIndex != C.INDEX_UNSET) {
            return CircularShuffleOrder(
                listener, startIndex, insertionCount,
                random.nextLong()
            )
        }
        // fall back to super which calls cloneAndInsert() which will process next shuffle order
        // or just randomly shuffles as appropriate
        return super.cloneAndSet(insertionCount, startIndex)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val numberOfElementsToRemove = indexToExclusive - indexFrom
        // short-circuit for performance and because this is allowed if nextShuffleOrder is set
        if (numberOfElementsToRemove == shuffled.size)
            return CircularShuffleOrder(listener, 0, 0, Random(random.nextLong()))
        if (listener.nextShuffleOrder != null)
            throw IllegalStateException("next shuffle order present but removing some items")
        val newShuffled = IntArray(shuffled.size - numberOfElementsToRemove)
        var foundElementsCount = 0

        for (i in shuffled.indices) {
            if (shuffled[i] in indexFrom..<indexToExclusive) {
                ++foundElementsCount
            } else {
                newShuffled[i - foundElementsCount] =
                    if (shuffled[i] >= indexFrom) shuffled[i] - numberOfElementsToRemove else shuffled[i]
            }
        }

        return CircularShuffleOrder(listener, newShuffled, Random(random.nextLong()))
    }

    override fun cloneAndMove(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int
    ): ShuffleOrder {
        return cloneAndRemove(indexFrom, indexToExclusive)
            .cloneAndInsert(newIndexFrom, indexToExclusive - indexFrom)
    }

    override fun cloneAndClear(): ShuffleOrder {
        return cloneAndRemove(0, shuffled.size)
    }

    @Parcelize
    class Persistent private constructor(val seed: Long, val data: IntArray?) : Parcelable {
        constructor(order: CircularShuffleOrder) : this(order.random.nextLong(), order.shuffled)

        companion object {
            fun deserialize(data: String?): Persistent {
                if (data == null || data.length < 2) return Persistent(Random.nextLong(), null)
                val split = data.split(';')
                if (split.isEmpty()) return Persistent(Random.nextLong(), null)
                return try {
                    Persistent(
                        split[0].toLong(), if (split.size > 1) split[1]
                            .split(',').map(String::toInt).toIntArray() else null
                    )
                } catch (e: NumberFormatException) {
                    // might happen with some real bad luck?
                    Log.e(
                        TAG,
                        "gave up trying to restore shuffle order: " + Log.getThrowableString(e)!!
                    )
                    Persistent(Random.nextLong(), null)
                }
            }
        }

        override fun toString(): String {
            return if (data != null) "$seed;${data.joinToString(",")}" else seed.toString()
        }

        fun toFactory(): (Int, Int, EndedWorkaroundPlayer) -> CircularShuffleOrder {
            if (data == null) {
                return { firstIndex, mediaItemCount, it ->
                    CircularShuffleOrder(it, firstIndex, mediaItemCount, seed)
                }
            } else {
                return { _, _, it ->
                    CircularShuffleOrder(it, data, seed)
                }
            }
        }
    }

}