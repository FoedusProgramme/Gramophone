/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.logic.utils.flows

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min

sealed class IncrementalList<T> {
    abstract val after: PersistentList<T>
    inline fun <R> mapNonCached(transform: (T) -> R): IncrementalList<R> {
        return when (this) {
            is Begin<T> -> Begin(after.map(transform).toPersistentList())
            is Insert<T> -> Insert(pos, count, after.map(transform).toPersistentList())
            is Remove<T> -> Remove(pos, count, after.map(transform).toPersistentList())
            is Move<T> -> Move(pos, count, outPos, after.map(transform).toPersistentList())
            is Update<T> -> Update(pos, count, after.map(transform).toPersistentList())
        }
    }
    data class Begin<T>(override val after: PersistentList<T>) : IncrementalList<T>()

    data class Insert<T>(val pos: Int, val count: Int, override val after: PersistentList<T>) :
        IncrementalList<T>()

    data class Remove<T>(val pos: Int, val count: Int, override val after: PersistentList<T>) : IncrementalList<T>()

    data class Move<T>(val pos: Int, val count: Int, val outPos: Int, override val after: PersistentList<T>) :
        IncrementalList<T>()

    data class Update<T>(val pos: Int, val count: Int, override val after: PersistentList<T>) :
        IncrementalList<T>()
}

sealed class IncrementalMap<T, R> {
    abstract val after: PersistentMap<T, R>
    inline fun <S> mapNonCached(transform: (T, R) -> S): IncrementalMap<T, S> {
        return when (this) {
            is Begin<T, R> -> Begin(after.mapValues { (key, value) -> transform(key, value) }.toPersistentMap())
            is Insert<T, R> -> Insert(key, after.mapValues { (key, value) -> transform(key, value) }.toPersistentMap())
            is Remove<T, R> -> Remove(key, after.mapValues { (key, value) -> transform(key, value) }.toPersistentMap())
            is Move<T, R> -> Move(key, outKey, after.mapValues { (key, value) -> transform(key, value) }.toPersistentMap())
            is Update<T, R> -> Update(key, after.mapValues { (key, value) -> transform(key, value) }.toPersistentMap())
        }
    }
    data class Begin<T, R>(override val after: PersistentMap<T, R>) : IncrementalMap<T, R>()
    data class Insert<T, R>(val key: T, override val after: PersistentMap<T, R>) : IncrementalMap<T, R>()
    data class Remove<T, R>(val key: T, override val after: PersistentMap<T, R>) : IncrementalMap<T, R>()
    data class Move<T, R>(val key: T, val outKey: T, override val after: PersistentMap<T, R>) : IncrementalMap<T, R>()
    data class Update<T, R>(val key: T, override val after: PersistentMap<T, R>) : IncrementalMap<T, R>()
}

typealias IncrementalSet<T> = IncrementalMap<T, Unit>

fun <T> Flow<IncrementalList<T>?>.defeatNullable() =
    map { it ?: IncrementalList.Begin(persistentListOf()) }

inline fun <T, R> Flow<IncrementalList<T>>.flatMapConcatIncremental(
    crossinline predicate: (T) -> List<R>
): Flow<IncrementalList<R>> = flow {
    var last: List<List<R>>? = null
    var lastFlat: PersistentList<R>? = null
    collect { command ->
        var new: List<List<R>>
        var newFlat: PersistentList<R>? = null
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map(predicate)
                newFlat = new.flatten().toPersistentList()
                emit(IncrementalList.Begin(newFlat))
            }

            command is IncrementalList.Insert -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos + command.count) {
                    val item = predicate(command.after[i])
                    totalSize += item.size
                    new.add(i, item)
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatten().toPersistentList()
                    emit(IncrementalList.Insert(totalStart, totalSize, newFlat))
                }
            }

            command is IncrementalList.Move -> {
                new = ArrayList(last!!)
                var totalSize = 0
                repeat(command.count) { _ ->
                    totalSize += new.removeAt(command.pos).size
                }
                for (i in command.outPos..<command.outPos + command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += last!![i].size
                    }
                    var totalOutStart = 0
                    for (i in 0..<command.outPos) {
                        totalOutStart += new[i].size
                    }
                    newFlat = new.flatten().toPersistentList()
                    emit(IncrementalList.Move(totalStart, totalSize, totalOutStart, newFlat))
                }
            }

            command is IncrementalList.Remove -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos + command.count) {
                    totalSize += new.removeAt(i).size
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatten().toPersistentList()
                    emit(IncrementalList.Remove(totalStart, totalSize, newFlat))
                }
            }

            command is IncrementalList.Update -> {
                new = ArrayList(last!!)
                var removed = 0
                var added = 0
                for (i in command.pos..<command.pos + command.count) {
                    removed += new[i].size
                    val item = predicate(command.after[i])
                    added += item.size
                    new[i] = item
                }
                if (removed != 0 || added != 0) {
                    var baseStart = 0
                    for (i in 0..<command.pos) {
                        baseStart += new[i].size
                    }
                    val baseSize = min(added, removed)
                    val offsetStart = baseStart + baseSize
                    val offsetCount = abs(added - removed)
                    newFlat = new.flatten().toPersistentList()
                    if (removed > added) {
                        val tempAfter = lastFlat!!.builder()
                        repeat(offsetCount) {
                            tempAfter.removeAt(offsetStart)
                        }
                        emit(IncrementalList.Remove(offsetStart, offsetCount, tempAfter.toPersistentList()))
                    } else if (removed < added) {
                        val tempAfter = lastFlat!!.builder()
                        for (i in offsetStart ..< offsetStart + offsetCount) {
                            tempAfter.add(i, newFlat[i])
                        }
                        emit(IncrementalList.Insert(offsetStart, offsetCount, tempAfter.toPersistentList()))
                    }
                    if (removed != 0 && added != 0) {
                        emit(IncrementalList.Update(baseStart, baseSize, newFlat))
                    }
                }
            }

            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
        lastFlat = newFlat ?: lastFlat
    }
}

inline fun <T, R> Flow<IncrementalList<T>>.flatMapLatestIncremental(
    crossinline predicate: (T) -> Flow<R>
): Flow<IncrementalList<R>> = mapIncremental(predicate).flattenLastestIncremental()

inline fun <T> Flow<IncrementalList<T>>.filterIncremental(
    crossinline predicate: (T) -> Boolean
): Flow<IncrementalList<T>> = flatMapConcatIncremental {
    if (predicate(it)) listOf(it) else emptyList()
}

inline fun <T> Flow<IncrementalList<T>>.filterLatestIncremental(
    crossinline predicate: (T) -> Flow<Boolean>
): Flow<IncrementalList<T>> = flatMapLatestIncremental { a ->
    predicate(a).map { a to it }
}.filterIncremental { it.second }.mapNonCachedIncremental { it.first }

@Suppress("UNCHECKED_CAST")
fun <T : Any> Flow<IncrementalList<T?>>.filterNotNullIncremental(): Flow<IncrementalList<T>> =
    filterIncremental { it != null } as Flow<IncrementalList<T>>

/*
   Hand-"optimized" version of:
     inline fun <T, R> Flow<IncrementalCommand<T>>.mapIncremental(
         crossinline predicate: (T) -> R
     ): Flow<IncrementalCommand<R>> = flatMapConcatIncremental {
         listOf(predicate(it))
     }
 */
inline fun <T, R> Flow<IncrementalList<T>>.mapIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalList<R>> = flow {
    var last: PersistentList<R>? = null
    collect { command ->
        var new: List<R>
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map(predicate).toPersistentList()
                emit(IncrementalList.Begin(new))
            }

            command is IncrementalList.Insert -> {
                new = last!!.builder()
                for (i in command.pos..<command.pos + command.count) {
                    new.add(i, predicate(command.after[i]))
                }
                new = new.toPersistentList()
                emit(IncrementalList.Insert(command.pos, command.count, new))
            }

            command is IncrementalList.Move -> {
                new = last!!.builder()
                repeat(command.count) { _ ->
                    new.removeAt(command.pos)
                }
                for (i in command.outPos..<command.outPos + command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                new = new.toPersistentList()
                emit(IncrementalList.Move(command.pos, command.count, command.outPos, new))
            }

            command is IncrementalList.Remove -> {
                new = last!!.builder()
                repeat(command.count) { _ ->
                    new.removeAt(command.pos)
                }
                new = new.toPersistentList()
                emit(IncrementalList.Remove(command.pos, command.count, new))
            }

            command is IncrementalList.Update -> {
                new = last!!.builder()
                for (i in command.pos..<command.pos + command.count) {
                    new[i] = predicate(command.after[i])
                }
                new = new.toPersistentList()
                emit(IncrementalList.Update(command.pos, command.count, new))
            }

            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
    }
}

inline fun <T, R> Flow<IncrementalList<T>>.mapNonCachedIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalList<R>> = map { it.mapNonCached(predicate) }

inline fun <T, K> Iterable<T>.groupByIndexed(keySelector: (Int, T) -> K): Map<K, List<T>> {
    val destination = LinkedHashMap<K, ArrayList<T>>()
    forEachIndexed { index, element ->
        val key = keySelector(index, element)
        val list = destination.getOrPut(key) { ArrayList() }
        list.add(element)
    }
    return destination
}

inline fun <T, R> Flow<IncrementalList<T>>.groupByIncremental(
    crossinline getKey: (T) -> R
): Flow<IncrementalMap<R, IncrementalList<T>>> = flow {
    var keyCache: ArrayList<R>? = null
    val groupCache = HashMap<R, IncrementalList<T>>()
    collect {
        when {
            it is IncrementalList.Begin || keyCache == null -> {
                groupCache.clear()
                if (keyCache != null)
                    keyCache!!.clear()
                else
                    keyCache = ArrayList()
                keyCache.addAll(it.after.map { getKey(it) })
                groupCache.putAll(it.after.groupByIndexed { i, _ -> keyCache[i] }
                    .mapValues { IncrementalList.Begin(it.value.toPersistentList()) })
                emit(IncrementalMap.Begin(groupCache.toPersistentMap()))
            }

            it is IncrementalList.Insert -> {
                for (i in it.pos..<it.pos + it.count) {
                    val item = it.after[i]
                    val key = getKey(item)
                    keyCache.add(i, key)
                    val group = groupCache[key]
                    if (group != null) {
                        var totalStart = 0
                        for (j in 0..<i) {
                            if (keyCache[j] == key) {// TODO optimize equals?
                                totalStart++
                            }
                        }
                        val new =
                            group.after.toMutableList().apply { add(totalStart, item) }.toPersistentList()
                        groupCache[key] = IncrementalList.Insert(totalStart, 1, new)
                        emit(IncrementalMap.Update(key, groupCache.toPersistentMap()))
                    } else {
                        groupCache[key] = IncrementalList.Begin(persistentListOf(item))
                        emit(IncrementalMap.Insert(key, groupCache.toPersistentMap()))
                    }
                }
            }

            it is IncrementalList.Move -> {
                val keys = mutableListOf<R>()
                repeat(it.count) { _ ->
                    keys.add(keyCache.removeAt(it.pos))
                }
                for (i in it.outPos..<it.outPos + it.count) {
                    keyCache.add(i, keys[i - it.outPos])
                }
                for (i in it.pos..<it.pos + it.count) {
                    val outPos = it.outPos - it.pos + i
                    val item = it.after[outPos]
                    val key = keys[i - it.pos]
                    val group = groupCache.getValue(key)
                    val oldInnerPos = group.after.indexOf(item)
                    var totalStart = 0
                    for (j in 0..<outPos) {
                        if (keyCache[j] == key) {// TODO optimize equals?
                            totalStart++
                        }
                    }
                    val new =
                        group.after.toMutableList().apply { add(totalStart, removeAt(oldInnerPos)) }
                            .toPersistentList()
                    groupCache[key] = IncrementalList.Move(oldInnerPos, totalStart, 1, new)
                    emit(IncrementalMap.Update(key, groupCache.toPersistentMap()))
                }
            }

            it is IncrementalList.Remove -> {
                repeat(it.count) { _ ->
                    val key = keyCache.removeAt(it.pos)
                    val group = groupCache.getValue(key)
                    if (group.after.size > 1) {
                        var totalStart = 0
                        for (j in 0..<it.pos) {
                            if (keyCache[j] == key) {// TODO optimize equals?
                                totalStart++
                            }
                        }
                        val new =
                            group.after.toMutableList().apply { removeAt(totalStart) }.toPersistentList()
                        groupCache[key] = IncrementalList.Remove(totalStart, 1, new)
                        emit(IncrementalMap.Update(key, groupCache.toPersistentMap()))
                    } else {
                        groupCache.remove(key)
                        emit(IncrementalMap.Remove(key, groupCache.toPersistentMap()))
                    }
                }
            }

            it is IncrementalList.Update -> {
                for (i in it.pos..<it.pos + it.count) {
                    val item = it.after[i]
                    val newKey = getKey(item)
                    val oldKey = keyCache[i]
                    val oldGroup = groupCache.getValue(oldKey)
                    if (newKey == oldKey) {
                        var totalStart = 0
                        for (j in it.pos - 1 downTo 0) {
                            if (keyCache[j] == oldKey) {// TODO optimize equals?
                                totalStart = oldGroup.after.indexOf(it.after[j])
                                break
                            }
                        }
                        totalStart++
                        val new = oldGroup.after.toMutableList().apply { this[totalStart] = item }
                            .toPersistentList()
                        groupCache[oldKey] = IncrementalList.Update(i, 1, new)
                        emit(IncrementalMap.Update(oldKey, groupCache.toPersistentMap()))
                        continue
                    }
                    keyCache[i] = newKey
                    if (oldGroup.after.size > 1) {
                        var totalStart = 0
                        for (j in it.pos - 1 downTo 0) {
                            if (keyCache[j] == oldKey) {// TODO optimize equals?
                                totalStart = oldGroup.after.indexOf(it.after[j])
                                break
                            }
                        }
                        totalStart++
                        val new =
                            oldGroup.after.toMutableList().apply { removeAt(totalStart) }.toPersistentList()
                        groupCache[oldKey] = IncrementalList.Remove(totalStart, 1, new)
                        emit(IncrementalMap.Update(oldKey, groupCache.toPersistentMap()))
                    } else {
                        groupCache.remove(oldKey)
                        emit(IncrementalMap.Remove(oldKey, groupCache.toPersistentMap()))
                    }
                    val group = groupCache[newKey]
                    if (group != null) {
                        var totalStart = 0
                        for (j in it.pos - 1 downTo 0) {
                            if (keyCache[j] == newKey) {// TODO optimize equals?
                                totalStart = group.after.indexOf(it.after[j])
                                break
                            }
                        }
                        totalStart++
                        val new =
                            group.after.toMutableList().apply { add(totalStart, item) }.toPersistentList()
                        groupCache[newKey] = IncrementalList.Insert(totalStart, 1, new)
                        emit(IncrementalMap.Update(newKey, groupCache.toPersistentMap()))
                    } else {
                        groupCache[newKey] = IncrementalList.Begin(persistentListOf(item))
                        emit(IncrementalMap.Insert(newKey, groupCache.toPersistentMap()))
                    }
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private suspend inline fun <T, R> ProducerScope<IncrementalMap<T, R>>.mergeCollector(
    state: HashMap<T, R>,
    otherState: HashMap<T, R>,
    otherWinsConflict: Boolean,
    it: IncrementalMap<T, R>
) {
    when (it) {
        is IncrementalMap.Begin -> {
            state.clear()
            state.putAll(it.after)
            send(IncrementalMap.Begin((if (otherWinsConflict) state + otherState else otherState + state).toPersistentMap()))
        }

        is IncrementalMap.Insert -> {
            state[it.key] = @Suppress("UNCHECKED_CAST") (it.after[it.key] as R)
            if (otherState.contains(it.key)) {
                if (!otherWinsConflict) {
                    send(IncrementalMap.Update(it.key, (otherState + state).toPersistentMap()))
                }
            } else {
                send(
                    IncrementalMap.Insert(
                        it.key, (if (otherWinsConflict) state + otherState else
                            otherState + state).toPersistentMap()
                    )
                )
            }
        }

        is IncrementalMap.Move -> {
            state[it.outKey] = @Suppress("UNCHECKED_CAST") (state.remove(it.key) as R)
            if (otherWinsConflict) {
                val containsOld = otherState.contains(it.key)
                val containsNew = otherState.contains(it.outKey)
                if (containsOld != containsNew) {
                    if (containsOld) {
                        send(IncrementalMap.Insert(it.outKey, (state + otherState).toPersistentMap()))
                    } else {
                        send(IncrementalMap.Remove(it.key, (state + otherState).toPersistentMap()))
                    }
                } else if (!containsOld /* && !containsNew */) {
                    send(IncrementalMap.Move(it.key, it.outKey, (state + otherState).toPersistentMap()))
                }
            } else {
                if (otherState.contains(it.outKey)) {
                    send(IncrementalMap.Remove(it.outKey, (otherState + state).toPersistentMap()))
                }
                send(IncrementalMap.Move(it.key, it.outKey, (otherState + state).toPersistentMap()))
                if (otherState.contains(it.key)) {
                    send(IncrementalMap.Insert(it.key, (otherState + state).toPersistentMap()))
                }
            }
        }

        is IncrementalMap.Remove -> {
            state.remove(it.key)
            if (otherState.contains(it.key)) {
                if (!otherWinsConflict) {
                    send(IncrementalMap.Update(it.key, (otherState + state).toPersistentMap()))
                }
            } else {
                send(
                    IncrementalMap.Remove(
                        it.key, (if (otherWinsConflict) state + otherState else
                            otherState + state).toPersistentMap()
                    )
                )
            }
        }

        is IncrementalMap.Update -> {
            state[it.key] = @Suppress("UNCHECKED_CAST") (it.after[it.key] as R)
            if (!otherWinsConflict || !otherState.contains(it.key)) {
                send(
                    IncrementalMap.Update(
                        it.key, (if (otherWinsConflict) state + otherState else
                            otherState + state).toPersistentMap()
                    )
                )
            }
        }
    }
}

fun <T, R> Flow<IncrementalMap<T, R>>.mergeWithIncremental(
    other: Flow<IncrementalMap<T, R>>,
    otherWinsConflict: Boolean = false
): Flow<IncrementalMap<T, R>> = channelFlow {
    coroutineScope {
        val lock = Mutex()
        var state1: HashMap<T, R>? = null
        var state2: HashMap<T, R>? = null
        val job1 = launch {
            this@mergeWithIncremental.collect {
                lock.withLock {
                    if (state1 == null) {
                        state1 = HashMap()
                        if (state2 != null) {
                            val cmd = IncrementalMap.Begin(it.after)
                            mergeCollector(state1, state2!!, otherWinsConflict, cmd)
                        }
                    } else if (state2 != null) {
                        mergeCollector(state1, state2!!, otherWinsConflict, it)
                    }
                }
            }
        }
        val job2 = launch {
            other.collect {
                lock.withLock {
                    if (state2 == null) {
                        state2 = HashMap()
                        if (state1 != null) {
                            val cmd = IncrementalMap.Begin(it.after)
                            mergeCollector(state2, state1, !otherWinsConflict, cmd)
                        }
                    } else if (state1 != null) {
                        mergeCollector(state2, state1, !otherWinsConflict, it)
                    }
                }
            }
        }
        job1.join()
        job2.join()
    }
}.drop(1) // drop the first Begin and wait for the second

@Suppress("NOTHING_TO_INLINE")
private inline fun <T> concatCollector(
    command: IncrementalList<T>, prefix: List<T>, suffix: List<T>
): IncrementalList<T> {
    val new = (prefix + command.after + suffix).toPersistentList()
    return when (command) {
        is IncrementalList.Begin<T> -> IncrementalList.Begin(new)
        is IncrementalList.Insert<T> -> IncrementalList.Insert(
            prefix.size + command.pos + suffix.size, command.count, new
        )
        is IncrementalList.Move<T> -> IncrementalList.Move(
            prefix.size + command.pos + suffix.size, command.count,
            prefix.size + command.outPos + suffix.size, new
        )
        is IncrementalList.Update<T> -> IncrementalList.Update(
            prefix.size + command.pos + suffix.size, command.count, new
        )
        is IncrementalList.Remove<T> -> IncrementalList.Remove(
            prefix.size + command.pos + suffix.size, command.count, new
        )
    }
}

fun <T> Flow<IncrementalList<T>>.concatIncremental(
    other: Flow<IncrementalList<T>>
): Flow<IncrementalList<T>> = channelFlow {
    coroutineScope {
        val lock = Mutex()
        var state1: PersistentList<T>? = null
        var state2: PersistentList<T>? = null
        val job1 = launch {
            this@concatIncremental.collect {
                lock.withLock {
                    state1 = it.after
                    if (state2 != null) {
                        send(concatCollector(it, emptyList(), state2!!))
                    }
                }
            }
        }
        val job2 = launch {
            other.collect {
                lock.withLock {
                    state2 = it.after
                    if (state1 != null) {
                        send(concatCollector(it, state1, emptyList()))
                    }
                }
            }
        }
        job1.join()
        job2.join()
    }
}

fun <T> Flow<IncrementalList<IncrementalList<T>>>.flattenConcatIncremental(): Flow<IncrementalList<T>> = flow {
    var last: PersistentList<PersistentList<T>>? = null
    collect { command ->
        var new: List<PersistentList<T>>
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map { it.after }.toPersistentList()
                emit(IncrementalList.Begin(new.flatten().toPersistentList()))
            }

            command is IncrementalList.Insert -> {
                new = last!!.builder()
                var addedSize = 0
                for (i in command.pos..<command.pos + command.count) {
                    val added = command.after[i]
                    new.add(i, added.after)
                    addedSize += added.after.size
                }
                new = new.toPersistentList()
                if (addedSize > 0) {
                    emit(IncrementalList.Insert(new.subList(0, command.pos)
                        .sumOf { it.size }, addedSize,
                        new.flatten().toPersistentList()))
                }
            }

            command is IncrementalList.Move -> {
                new = last!!.builder()
                var movedSize = 0
                repeat(command.count) { _ ->
                    movedSize += new.removeAt(command.pos).size
                }
                val outPosFlattened = new.subList(0, command.outPos).sumOf { it.size }
                for (i in command.outPos..<command.outPos + command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                new = new.toPersistentList()
                if (movedSize > 0) {
                    emit(IncrementalList.Move(last!!.subList(0, command.pos)
                        .sumOf { it.size }, movedSize, outPosFlattened,
                        new.flatten().toPersistentList()))
                }
            }

            command is IncrementalList.Remove -> {
                new = last!!.builder()
                var removedSize = 0
                repeat(command.count) { _ ->
                    removedSize += new.removeAt(command.pos).size
                }
                new = new.toPersistentList()
                if (removedSize > 0) {
                    emit(IncrementalList.Remove(new.subList(0, command.pos)
                        .sumOf { it.size }, removedSize,
                        new.flatten().toPersistentList()))
                }
            }

            command is IncrementalList.Update -> {
                new = last!!.builder()
                for (i in command.pos..<command.pos + command.count) {
                    val updated = command.after[i]
                    new[i] = updated.after
                    emit(concatCollector(updated, new.subList(0, i)
                        .flatten(), if (i + 1 < new.size) new.subList(i + 1, new.size)
                            .flatten() else emptyList()))
                }
                new = new.toPersistentList()
            }

            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
    }
}

inline fun <T> Flow<IncrementalSet<T>>.filterIncremental(
    crossinline predicate: (T) -> Boolean
): Flow<IncrementalSet<T>> = filterIncremental { it, _ -> predicate(it) }

inline fun <T, R> Flow<IncrementalMap<T, R>>.filterIncremental(
    crossinline predicate: (T, R) -> Boolean
): Flow<IncrementalMap<T, R>> = flow {
    var filterCache: HashMap<T, Boolean>? = null
    collect {
        when {
            it is IncrementalMap.Begin || filterCache == null -> {
                if (filterCache != null)
                    filterCache!!.clear()
                else
                    filterCache = HashMap()
                filterCache.putAll(it.after.mapValues { predicate(it.key, it.value) })
                emit(IncrementalMap.Begin(it.after.filter { filterCache.getValue(it.key) }.toPersistentMap()))
            }

            it is IncrementalMap.Insert -> {
                val filtered =
                    predicate(it.key, @Suppress("UNCHECKED_CAST") (it.after[it.key] as R))
                filterCache[it.key] = filtered
                if (!filtered)
                    emit(
                        IncrementalMap.Insert(
                            it.key,
                            it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                    )
            }

            it is IncrementalMap.Move -> {
                val filtered = filterCache.remove(it.key)!!
                filterCache[it.outKey] = filtered
                if (!filtered)
                    emit(
                        IncrementalMap.Move(
                            it.key,
                            it.outKey,
                            it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                    )
            }

            it is IncrementalMap.Remove -> {
                if (!filterCache.remove(it.key)!!)
                    emit(
                        IncrementalMap.Remove(
                            it.key,
                            it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                    )
            }

            it is IncrementalMap.Update -> {
                val wasFiltered = filterCache.getValue(it.key)
                val filtered =
                    predicate(it.key, @Suppress("UNCHECKED_CAST") (it.after[it.key] as R))
                if (wasFiltered != filtered) {
                    filterCache[it.key] = filtered
                    if (wasFiltered) {
                        emit(
                            IncrementalMap.Insert(
                                it.key,
                                it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                        )
                    } else /* if (filtered) */ {
                        emit(
                            IncrementalMap.Remove(
                                it.key,
                                it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                        )
                    }
                } else if (!filtered) {
                    emit(
                        IncrementalMap.Update(
                            it.key,
                            it.after.filter { filterCache.getValue(it.key) }.toPersistentMap())
                    )
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any, R> Flow<IncrementalMap<T?, R>>.filterKeyNotNullIncremental(): Flow<IncrementalMap<T, R>> =
    filterIncremental { t, _ -> t != null } as Flow<IncrementalMap<T, R>>

@Suppress("UNCHECKED_CAST")
fun <T, R : Any> Flow<IncrementalMap<T, R?>>.filterValueNotNullIncremental(): Flow<IncrementalMap<T, R>> =
    filterIncremental { _, t -> t != null } as Flow<IncrementalMap<T, R>>

inline fun <T, R> Flow<IncrementalSet<T>>.mapNonCachedIncremental(
    crossinline fastPredicate: (T) -> R
): Flow<IncrementalMap<T, R>> = mapNonCachedIncremental { it, _ -> fastPredicate(it) }

inline fun <T, R, S> Flow<IncrementalMap<T, R>>.mapNonCachedIncremental(
    crossinline fastPredicate: (T, R) -> S
): Flow<IncrementalMap<T, S>> = flow {
    collect {
        when (it) {
            is IncrementalMap.Begin -> {
                emit(IncrementalMap.Begin(it.after.mapValues { fastPredicate(it.key, it.value) }.toPersistentMap()))
            }

            is IncrementalMap.Insert -> {
                emit(
                    IncrementalMap.Insert(
                        it.key,
                        it.after.mapValues { fastPredicate(it.key, it.value) }.toPersistentMap())
                )
            }

            is IncrementalMap.Move -> {
                emit(
                    IncrementalMap.Move(
                        it.key,
                        it.outKey,
                        it.after.mapValues { fastPredicate(it.key, it.value) }.toPersistentMap())
                )
            }

            is IncrementalMap.Remove -> {
                emit(
                    IncrementalMap.Remove(
                        it.key,
                        it.after.mapValues { fastPredicate(it.key, it.value) }.toPersistentMap())
                )
            }

            is IncrementalMap.Update -> {
                emit(
                    IncrementalMap.Update(
                        it.key,
                        it.after.mapValues { fastPredicate(it.key, it.value) }.toPersistentMap())
                )
            }
        }
    }
}

fun <T, R> Flow<IncrementalMap<T, R>>.keySetAsSortedIncrementalList(
    comparator: Comparator<T>
): Flow<IncrementalList<T>> = flow {
    var keys: ArrayList<T>? = null
    collect {
        when {
            it is IncrementalMap.Begin || keys == null -> {
                keys = ArrayList(it.after.keys.sortedWith(comparator))
                emit(IncrementalList.Begin(keys.toPersistentList()))
            }

            it is IncrementalMap.Insert -> {
                val idx = keys.binarySearch(it.key, comparator).let {
                    if (it >= 0) it else -it - 1
                }
                keys.add(idx, it.key)
                emit(IncrementalList.Insert(idx, 1,
                    keys.toPersistentList()))
            }

            it is IncrementalMap.Move -> {
                // either stays at same sorted pos and is Update, or it's Remove+Insert
                val oldIdx = keys.indexOf(it.key)
                keys.removeAt(oldIdx)
                val idx = keys.binarySearch(it.key, comparator).let {
                    if (it >= 0) it else -it - 1
                }
                if (oldIdx == idx) {
                    keys.add(idx, it.key)
                    emit(IncrementalList.Update(idx,
                        1, keys.toPersistentList()))
                } else {
                    emit(IncrementalList.Remove(oldIdx,
                            1, keys.toPersistentList()))
                    keys.add(idx, it.key)
                    emit(IncrementalList.Insert(idx, 1,
                        keys.toPersistentList()))
                }
            }

            it is IncrementalMap.Remove -> {
                emit(
                    IncrementalList.Remove(
                        keys.indexOf(it.key).also { i -> keys.removeAt(i) },
                        1, keys.toPersistentList())
                )
            }

            it is IncrementalMap.Update -> {}
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, R>>.isEmptyIncremental(): Flow<Boolean> =
    map { it.after.isEmpty() }.distinctUntilChanged()

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Flow<IncrementalList<T>>.isEmptyIncremental(): Flow<Boolean> =
    map { it.after.isEmpty() }.distinctUntilChanged()

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, R>>.withoutValuesIncremental(): Flow<IncrementalSet<T>> = flow {
    collect {
        when {
            it is IncrementalMap.Update -> {}
            else -> emit(it.mapNonCached { _, _ -> Unit })
        }
    }
}

inline fun <T, R> Flow<IncrementalSet<T>>.mapIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalMap<T, R>> = mapIncremental { it, _ -> predicate(it) }

inline fun <T, R, S> Flow<IncrementalMap<T, R>>.mapIncremental(
    crossinline predicate: (T, R) -> S
): Flow<IncrementalMap<T, S>> = flow {
    var mapCache: HashMap<T, S>? = null
    collect {
        when {
            it is IncrementalMap.Begin || mapCache == null -> {
                if (mapCache != null)
                    mapCache!!.clear()
                else
                    mapCache = HashMap()
                mapCache.putAll(it.after.mapValues { predicate(it.key, it.value) })
                emit(IncrementalMap.Begin(it.after.mapValues { mapCache.getValue(it.key) }.toPersistentMap()))
            }

            it is IncrementalMap.Insert -> {
                mapCache[it.key] =
                    predicate(it.key, @Suppress("UNCHECKED_CAST") (it.after[it.key] as R))
                emit(
                    IncrementalMap.Insert(
                        it.key,
                        it.after.mapValues { mapCache.getValue(it.key) }.toPersistentMap())
                )
            }

            it is IncrementalMap.Move -> {
                mapCache[it.outKey] = mapCache.remove(it.key)!!
                emit(
                    IncrementalMap.Move(
                        it.key,
                        it.outKey,
                        it.after.mapValues { mapCache.getValue(it.key) }.toPersistentMap())
                )
            }

            it is IncrementalMap.Remove -> {
                mapCache.remove(it.key)
                emit(
                    IncrementalMap.Remove(
                        it.key,
                        it.after.mapValues { mapCache.getValue(it.key) }.toPersistentMap())
                )
            }

            it is IncrementalMap.Update -> {
                mapCache[it.key] =
                    predicate(it.key, @Suppress("UNCHECKED_CAST") (it.after[it.key] as R))
                emit(
                    IncrementalMap.Update(
                        it.key,
                        it.after.mapValues { mapCache.getValue(it.key) }.toPersistentMap())
                )
            }
        }
    }
}

inline fun <T, R, S> Flow<IncrementalMap<T, R>>.flatMapLatestWithInputAsFlowIncremental(
    crossinline operator: (T, Flow<R>) -> Flow<S>
): Flow<IncrementalMap<T, S>> =
    withoutValuesIncremental()
        .flatMapLatestIncremental { key, _ -> operator(key, forKey(key).filterNotNull()) }

inline fun <T, R> Flow<IncrementalSet<T>>.flatMapLatestIncremental(
    crossinline predicate: (T) -> Flow<R>
): Flow<IncrementalMap<T, R>> = flatMapLatestIncremental { it, _ -> predicate(it) }

inline fun <T, R, S> Flow<IncrementalMap<T, R>>.flatMapLatestIncremental(
    crossinline predicate: (T, R) -> Flow<S>
): Flow<IncrementalMap<T, S>> = mapIncremental(predicate).flattenLastestIncremental()

inline fun <T> Flow<IncrementalSet<T>>.filterLatestIncremental(
    crossinline predicate: (T) -> Flow<Boolean>
): Flow<IncrementalSet<T>> = filterLatestIncremental { it, _ -> predicate(it) }

inline fun <T, R> Flow<IncrementalMap<T, R>>.filterLatestIncremental(
    crossinline predicate: (T, R) -> Flow<Boolean>
): Flow<IncrementalMap<T, R>> = flatMapLatestIncremental { a, b ->
    predicate(a, b).distinctUntilChanged().map { b to it }
}.filterIncremental { _, b -> b.second }.mapNonCachedIncremental { _, b -> b.first }

@PublishedApi
internal inline fun <T, R> CoroutineScope.createFlattenJob(
    key: R, flow: Flow<T>, crossinline update: suspend (() -> R, T) -> Unit
): Pair<Job, AtomicReference<R>> {
    val keyReference = AtomicReference(key)
    return launch {
        flow.collect {
            update({ keyReference.get() }, it)
        }
    } to keyReference
}

@PublishedApi
internal sealed class PendingCommand {
    private val deferred = CompletableDeferred<Unit>()
    fun complete() = deferred.complete(Unit)
    suspend fun await() = deferred.await()
    class Begin : PendingCommand()
    class Insert : PendingCommand()
    class Update : PendingCommand()
}

@Suppress("NOTHING_TO_INLINE")
@JvmName("flattenLastestIncrementalList")
inline fun <T> Flow<IncrementalList<Flow<T>>>.flattenLastestIncremental(): Flow<IncrementalList<T>> =
    channelFlow {
        coroutineScope {
            val lock = Mutex()
            var state: ArrayList<Pair<Job, AtomicReference<Int>>>? = null
            val outputState = ArrayList<T>()
            val pendingKeys = HashSet<Int>()
            var pending: PendingCommand? = null
            val update: suspend (getKeyLocked: () -> Int, value: T) -> Unit = { getKeyLocked, b ->
                lock.withLock {
                    val a = getKeyLocked()
                    outputState[a] = b
                    if (pending != null && pendingKeys.contains(a)) {
                        pendingKeys.remove(a)
                        if (pendingKeys.isEmpty()) {
                            when (pending!!) {
                                is PendingCommand.Begin -> send(IncrementalList.Begin(outputState.toPersistentList()))
                                is PendingCommand.Insert -> send(
                                    IncrementalList.Insert(
                                        a, 1,
                                        outputState.toPersistentList()
                                    )
                                )

                                is PendingCommand.Update -> send(
                                    IncrementalList.Update(
                                        a, 1,
                                        outputState.toPersistentList()
                                    )
                                )
                            }
                            pending!!.complete()
                            pending = null
                        }
                    } else {
                        send(IncrementalList.Update(a, 1, outputState.toPersistentList()))
                    }
                }
            }
            collect {
                lock.withLock {
                    when {
                        it is IncrementalList.Begin || state == null -> {
                            if (state != null) {
                                state!!.forEach { it.first.cancel() }
                                // must join all to avoid old jobs accessing outputState
                                state!!.forEach { it.first.join() }
                                state!!.clear()
                                outputState.clear()
                            } else state = ArrayList()
                            val deferred = PendingCommand.Begin()
                            pending = deferred
                            pendingKeys.addAll(it.after.indices)
                            state.addAll(it.after.mapIndexed { i, it ->
                                createFlattenJob(
                                    i,
                                    it,
                                    update
                                )
                            })
                            deferred.await()
                        }

                        it is IncrementalList.Insert -> {
                            val deferred = PendingCommand.Insert()
                            pending = deferred
                            for (i in it.pos..<it.pos + it.count) {
                                pendingKeys.add(i)
                                state[i] = createFlattenJob(i, it.after[i], update)
                            }
                            deferred.await()
                        }

                        it is IncrementalList.Move -> {
                            for (i in it.pos..<it.pos + it.count) {
                                val item = state.removeAt(i)
                                item.second.set(it.outPos - it.pos + i)
                                state[it.outPos - it.pos + i] = item
                                outputState[it.outPos - it.pos + i] = outputState.removeAt(i)
                                send(
                                    IncrementalList.Move(
                                        i,
                                        1,
                                        it.outPos - it.pos + i,
                                        outputState.toPersistentList()
                                    )
                                )
                            }
                        }

                        it is IncrementalList.Remove -> {
                            for (i in it.pos..<it.pos + it.count) {
                                state.removeAt(i).first.cancelAndJoin()
                                outputState.removeAt(i)
                                send(
                                    IncrementalList.Remove(
                                        i,
                                        1,
                                        outputState.toPersistentList()
                                    )
                                )
                            }
                        }

                        it is IncrementalList.Update -> {
                            repeat(it.count) { _ ->
                                state.removeAt(it.pos).first.cancelAndJoin()
                            }
                            val deferred = PendingCommand.Update()
                            pending = deferred
                            for (i in it.pos..<it.pos + it.count) {
                                pendingKeys.add(i)
                                state[i] = createFlattenJob(i, it.after[i], update)
                            }
                            deferred.await()
                        }
                    }
                }
            }
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, Flow<R>>>.flattenLastestIncremental(): Flow<IncrementalMap<T, R>> =
    channelFlow {
        coroutineScope {
            val lock = Mutex()
            var state: HashMap<T, Pair<Job, AtomicReference<T>>>? = null
            val outputState = HashMap<T, R>()
            val pendingKeys = HashSet<T>()
            var pending: PendingCommand? = null
            val update: suspend (getKeyLocked: () -> T, value: R) -> Unit = { getKeyLocked, b ->
                lock.withLock {
                    val a = getKeyLocked()
                    outputState[a] = b
                    if (pending != null && pendingKeys.contains(a)) {
                        pendingKeys.remove(a)
                        if (pendingKeys.isEmpty()) {
                            when (pending!!) {
                                is PendingCommand.Begin -> send(IncrementalMap.Begin(outputState.toPersistentMap()))
                                is PendingCommand.Insert -> send(
                                    IncrementalMap.Insert(
                                        a,
                                        outputState.toPersistentMap()
                                    )
                                )

                                is PendingCommand.Update -> send(
                                    IncrementalMap.Update(
                                        a,
                                        outputState.toPersistentMap()
                                    )
                                )
                            }
                            pending!!.complete()
                            pending = null
                        }
                    } else {
                        send(IncrementalMap.Update(a, outputState.toPersistentMap()))
                    }
                }
            }
            collect {
                when {
                    it is IncrementalMap.Begin || state == null -> {
                        if (state != null) {
                            state!!.forEach { it.value.first.cancel() }
                            // must join all to avoid old jobs accessing outputState
                            state!!.forEach { it.value.first.join() }
                            state!!.clear()
                            outputState.clear()
                        } else state = HashMap()
                        val deferred = PendingCommand.Begin()
                        pending = deferred
                        pendingKeys.addAll(it.after.keys)
                        state.putAll(it.after.mapValues {
                            createFlattenJob(
                                it.key,
                                it.value,
                                update
                            )
                        })
                        deferred.await()
                    }

                    it is IncrementalMap.Insert -> {
                        val deferred = PendingCommand.Insert()
                        pending = deferred
                        pendingKeys.add(it.key)
                        state[it.key] = createFlattenJob(it.key, it.after.getValue(it.key), update)
                        deferred.await()
                    }

                    it is IncrementalMap.Move -> {
                        lock.withLock {
                            val item = state.remove(it.key)!!
                            item.second.set(it.outKey)
                            state[it.outKey] = item
                            outputState[it.outKey] =
                                @Suppress("UNCHECKED_CAST") (outputState.remove(it.key) as R)
                            send(IncrementalMap.Move(it.key, it.outKey, outputState.toPersistentMap()))
                        }
                    }

                    it is IncrementalMap.Remove -> {
                        lock.withLock {
                            state.remove(it.key)!!.first.cancelAndJoin()
                            outputState.remove(it.key)
                            send(IncrementalMap.Remove(it.key, outputState.toPersistentMap()))
                        }
                    }

                    it is IncrementalMap.Update -> {
                        lock.withLock {
                            state.remove(it.key)!!.first.cancelAndJoin()
                        }
                        val deferred = PendingCommand.Update()
                        pending = deferred
                        pendingKeys.add(it.key)
                        state[it.key] = createFlattenJob(it.key, it.after.getValue(it.key), update)
                        deferred.await()
                    }
                }
            }
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, R>>.toIncrementalList(
    fastComparator: Comparator<T>
): Flow<IncrementalList<R>> = flow {
    var keys: ArrayList<T>? = null
    val values = ArrayList<R>()

    collect { event ->
        when {
            event is IncrementalMap.Begin || keys == null -> {
                if (keys != null)
                    keys!!.clear()
                else
                    keys = ArrayList()
                values.clear()
                event.after.keys.sortedWith(fastComparator).forEach { k ->
                    keys.add(k)
                    values.add(event.after.getValue(k))
                }
                emit(IncrementalList.Begin(values.toPersistentList()))
            }

            event is IncrementalMap.Insert -> {
                val key = event.key
                val value = event.after.getValue(key)
                val idx = keys.binarySearch(key, fastComparator).let {
                    if (it >= 0) it else -it - 1
                }
                keys.add(idx, key)
                values.add(idx, value)
                emit(IncrementalList.Insert(idx, 1, values.toPersistentList()))
            }

            event is IncrementalMap.Update -> {
                val key = event.key
                val value = event.after.getValue(key)
                val idx = keys.indexOf(key).takeIf { it >= 0 }
                    ?: throw IllegalStateException("Key not found for update: $key")
                values[idx] = value
                emit(IncrementalList.Update(idx, 1, values.toPersistentList()))
            }

            event is IncrementalMap.Move -> {
                val oldKey = event.key
                val newKey = event.outKey
                val fromIdx = keys.indexOf(oldKey).takeIf { it >= 0 }
                    ?: throw IllegalStateException("Key not found for move: $oldKey")
                keys.removeAt(fromIdx)
                val movedValue = values.removeAt(fromIdx)
                val toIdx = keys.binarySearch(newKey, fastComparator).let {
                    if (it >= 0) it else -it - 1
                }
                keys.add(toIdx, newKey)
                values.add(toIdx, movedValue)
                emit(IncrementalList.Move(fromIdx, toIdx, 1, values.toPersistentList()))
            }

            event is IncrementalMap.Remove -> {
                val key = event.key
                val idx = keys.indexOf(key).takeIf { it >= 0 }
                    ?: throw IllegalStateException("Key not found for remove: $key")
                keys.removeAt(idx)
                values.removeAt(idx)
                emit(IncrementalList.Remove(idx, 1, values.toPersistentList()))
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, R>>.hasKey(
    key: T
): Flow<Boolean> = flow {
    collect {
        when (it) {
            is IncrementalMap.Begin -> emit(it.after.containsKey(key))
            is IncrementalMap.Insert -> if (it.key == key) emit(true)
            is IncrementalMap.Move -> if (it.outKey == key) emit(true) else
                if (it.key == key) emit(false)
            is IncrementalMap.Remove -> if (it.key == key) emit(false)
            is IncrementalMap.Update -> {}
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R> Flow<IncrementalMap<T, R>>.forKey(
    key: T
): Flow<R?> = flow {
    collect {
        val shouldEmit = when (it) {
            // TODO will Begin handling lead to correct behaviour combined with groupBy and merge
            is IncrementalMap.Begin -> true
            is IncrementalMap.Insert -> it.key == key
            is IncrementalMap.Move -> it.key == key || it.outKey == key
            is IncrementalMap.Remove -> it.key == key
            is IncrementalMap.Update -> it.key == key
        }
        if (shouldEmit)
            emit(it.after[key])
    }
}

inline fun <T> Flow<PersistentList<T>>.listFlowToIncrementalList(
    crossinline areItemsTheSame: (T, T) -> Boolean,
    crossinline areContentsTheSame: (T, T) -> Boolean
): Flow<IncrementalList<T>> =
    channelFlow {
        var lastValue: PersistentList<T>? = null
        collect {
            val lastValueCache = lastValue
            if (lastValueCache == null) {
                send(IncrementalList.Begin(it))
            } else {
                val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int {
                        return lastValueCache.size
                    }

                    override fun getNewListSize(): Int {
                        return it.size
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return areItemsTheSame(lastValueCache[oldItemPosition],
                            it[newItemPosition])
                    }

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return areContentsTheSame(lastValueCache[oldItemPosition],
                            it[newItemPosition])
                    }
                }, true)
                val newListScratch = lastValueCache.builder()
                diff.dispatchUpdatesTo(object : ListUpdateCallback {
                    override fun onInserted(position: Int, count: Int) {
                        for (i in position..<position + count) {
                            newListScratch.add(i, it[i]) // TODO is i both old&new pos
                        }
                        trySend(
                            IncrementalList.Insert(
                                position, count, newListScratch.build()
                                    .also { lastValue = it })
                        )
                    }

                    override fun onRemoved(position: Int, count: Int) {
                        repeat(count) {
                            newListScratch.removeAt(position)
                        }
                        trySend(
                            IncrementalList.Remove(
                                position,
                                count, newListScratch.build()
                                    .also { lastValue = it })
                        )
                    }

                    override fun onMoved(
                        fromPosition: Int,
                        toPosition: Int
                    ) {
                        newListScratch.add(toPosition, newListScratch.removeAt(fromPosition))
                        trySend(
                            IncrementalList.Move(
                                fromPosition,
                                1, toPosition, newListScratch.build()
                                    .also { lastValue = it })
                        )
                    }

                    override fun onChanged(
                        position: Int,
                        count: Int,
                        payload: Any?
                    ) {
                        for (i in position..<position + count) {
                            newListScratch[i] = it[i] // TODO is i both old&new pos
                        }
                        trySend(
                            IncrementalList.Update(
                                position, count, newListScratch.build()
                                    .also { lastValue = it })
                        )
                    }
                })
            }
        }
    }

// TODO unit tests