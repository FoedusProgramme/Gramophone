package org.akanework.gramophone

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.logic.utils.flows.IncrementalList
import org.akanework.gramophone.logic.utils.flows.IncrementalMap
import org.akanework.gramophone.logic.utils.flows.PauseManager
import org.akanework.gramophone.logic.utils.flows.conflateAndBlockWhenPaused
import org.akanework.gramophone.logic.utils.flows.filterIncremental
import org.akanework.gramophone.logic.utils.flows.flatMapConcatIncremental
import org.akanework.gramophone.logic.utils.flows.flattenLastestIncremental
import org.akanework.gramophone.logic.utils.flows.forKey
import org.akanework.gramophone.logic.utils.flows.groupByIncremental
import org.akanework.gramophone.logic.utils.flows.mapIncremental
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseableFlowsTest {
    @Test
    fun testPauseableFlows() {
        val out = arrayListOf<Int>()
        val inFlow = flow {
            var i = 0
            while (i < 10) {
                println("flow: wait 100ms")
                delay(100)
                val start = System.currentTimeMillis()
                println("flow: emit $i")
                emit(i++)
                println("flow: emit ${i - 1} took ${System.currentTimeMillis() - start}")
            }
        }.conflateAndBlockWhenPaused()
        runBlocking {
            val tpm = TestPauseManager(true)
            val job = launch(Dispatchers.IO.limitedParallelism(1) + tpm) {
                inFlow.collect {
                    println("channel recv $it")
                    out.add(it)
                }
            }
            println("#start: $out")
            delay(250)
            println("#0, setting pause = false...: $out")
            tpm.isPaused.value = false
            println("#1, set pause = false!!: $out")
            delay(200)
            println("#2, setting pause = true...: $out")
            tpm.isPaused.value = true
            println("#3, set pause = true-..: $out")
            delay(300)
            println("#4, setting pause = false...: $out")
            tpm.isPaused.value = false
            println("#5, set pause = false: $out")
            delay(500)
            assertEquals(arrayListOf(1, 2, 3, 6, 7, 8, 9), out)
            job.join()
        }
    }

    class TestPauseManager(initialValue: Boolean) : PauseManager {
        override val isPaused = MutableStateFlow<Boolean>(initialValue)
    }

    @Test
    fun incrementalFlows() {
        runBlocking {
            var countEmitted = 0
            var countMapped = 0
            var countFiltered = 0
            val source = flow {
                emit(IncrementalList.Begin(persistentListOf(1, 2, 3)))
                emit(IncrementalList.Insert(1, 2, persistentListOf(1, 15, 10, 2, 3)))
                emit(IncrementalList.Insert(1, 1, persistentListOf(1, 999, 15, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 2, persistentListOf(1, 15, 999, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 5, persistentListOf(1, 999, 10, 2, 3, 15)))
                emit(IncrementalList.Move(2, 3, 0, persistentListOf(10, 2, 3, 1, 999, 15)))
                emit(IncrementalList.Remove(1, 1, persistentListOf(10, 3, 1, 999, 15)))
                emit(IncrementalList.Update(1, 1, persistentListOf(10, 5, 1, 999, 15)))
            }
                .assertContractNotViolated("init")
                .onEach { countEmitted++ }
                .mapIncremental { it + 1 }
                .assertContractNotViolated("after map")
                .onEach { countMapped++ }
                .filterIncremental { it < 100 }
                .assertContractNotViolated("after filter")
                .onEach { countFiltered++ }
                .flatMapConcatIncremental { if (it % 2 == 0) persistentListOf(it, it) else emptyList() }
                .assertContractNotViolated("after flatMap")
            val out = ArrayList<IncrementalList<Int>>()
            source.toCollection(out)
            assertEquals(8, countEmitted)
            assertEquals(8, countMapped)
            assertEquals(6, countFiltered)
            assertEquals(5, out.size)
            assertTrue(out[0] is IncrementalList.Begin)
            assertTrue(out[1] is IncrementalList.Insert)
            assertTrue(out[2] is IncrementalList.Move)
            assertTrue(out[3] is IncrementalList.Move)
            assertTrue(out[4] is IncrementalList.Update)
            assertEquals(persistentListOf(2, 2, 16, 16, 4, 4), out[1].after)
            assertEquals(persistentListOf(4, 4, 2, 2, 16, 16), out[3].after)
            assertEquals(persistentListOf(6, 6, 2, 2, 16, 16), out[4].after)
        }
    }

    @Test
    fun incrementalFlowsGroupBy() {
        runBlocking {
            var countEmitted = 0
            var countMapped = 0
            var countFiltered = 0
            val source = flow {
                emit(IncrementalList.Begin(persistentListOf(1, 2, 3)))
                emit(IncrementalList.Insert(1, 2, persistentListOf(1, 15, 10, 2, 3)))
                emit(IncrementalList.Insert(1, 1, persistentListOf(1, 999, 15, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 2, persistentListOf(1, 15, 999, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 5, persistentListOf(1, 999, 10, 2, 3, 15)))
                emit(IncrementalList.Move(2, 3, 0, persistentListOf(10, 2, 3, 1, 999, 15)))
                emit(IncrementalList.Remove(1, 1, persistentListOf(10, 3, 1, 999, 15)))
                emit(IncrementalList.Update(1, 1, persistentListOf(10, 5, 1, 999, 15)))
            }
                .assertContractNotViolated("init")
                .onEach { countEmitted++ }
                .mapIncremental { it + 1 }
                .assertContractNotViolated("after map")
                .onEach { countMapped++ }
                .filterIncremental { it < 100 }
                .assertContractNotViolated("after filter")
                .onEach { countFiltered++ }
                .groupByIncremental { it % 2 }
                .assertContractNotViolated("after groupBy")
                .mapIncremental { a, b -> flowOf(b) }
                .assertContractNotViolated("after mapIncremental")
                .flattenLastestIncremental()
                .assertContractNotViolated("after flattenIncremental")
                .forKey(1)
                .map { it!! }
                .assertContractNotViolated("after forKey")
            source.collect()
        }
    }


    @Test
    fun incrementalFlowsGroupBy2() {
        runBlocking {
            var countEmitted = 0
            var countMapped = 0
            var countFiltered = 0
            val source = flow {
                emit(IncrementalList.Begin(persistentListOf(1, 2, 3)))
                emit(IncrementalList.Insert(1, 2, persistentListOf(1, 15, 10, 2, 3)))
                emit(IncrementalList.Insert(1, 1, persistentListOf(1, 999, 15, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 2, persistentListOf(1, 15, 999, 10, 2, 3)))
                emit(IncrementalList.Move(1, 1, 5, persistentListOf(1, 999, 10, 2, 3, 15)))
                emit(IncrementalList.Move(2, 3, 0, persistentListOf(10, 2, 3, 1, 999, 15)))
                emit(IncrementalList.Remove(1, 1, persistentListOf(10, 3, 1, 999, 15)))
                emit(IncrementalList.Update(1, 1, persistentListOf(10, 5, 1, 999, 15)))
            }
                .assertContractNotViolated("init")
                .onEach { countEmitted++ }
                .mapIncremental { it + 1 }
                .assertContractNotViolated("after map")
                .onEach { countMapped++ }
                .filterIncremental { it < 100 }
                .assertContractNotViolated("after filter")
                .onEach { countFiltered++ }
                .groupByIncremental { it % 2 }
                .assertContractNotViolated("after groupBy")
                .forKey(1)
                .map { it!! }
                .assertContractNotViolated("after forKey")
            source.collect()
        }
    }

    fun <T> Flow<IncrementalList<T>>.assertContractNotViolated(tag: String) = flow {
        var cache: IncrementalList<T>? = null
        collect {
            when {
                it is IncrementalList.Begin || cache == null -> {
                    // nothing to check
                }

                it is IncrementalList.Insert -> {
                    var new = ArrayList(cache!!.after)
                    for (i in it.pos..<it.pos + it.count) {
                        new.add(i, it.after[i])
                    }
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalList.Move -> {
                    var new = ArrayList(cache!!.after)
                    var removed = ArrayList<T>(it.count)
                    repeat(it.count) { _ ->
                        removed.add(new.removeAt(it.pos))
                    }
                    for (i in it.outPos..<it.outPos + it.count) {
                        new.add(i, removed[i - it.outPos])
                    }
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalList.Remove -> {
                    var new = ArrayList(cache!!.after)
                    repeat(it.count) { _ ->
                        new.removeAt(it.pos)
                    }
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalList.Update -> {
                    var new = ArrayList(cache!!.after)
                    for (i in it.pos..<it.pos + it.count) {
                        new[i] = it.after[i]
                    }
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                else -> throw IllegalArgumentException("unknown command?")
            }
            emit(it)
            cache = it
        }
    }

    @JvmName("assertContractNotViolatedMap")
    fun <T, R> Flow<IncrementalMap<T, R>>.assertContractNotViolated(tag: String) = flow {
        var cache: IncrementalMap<T, R>? = null
        collect {
            when {
                it is IncrementalMap.Begin || cache == null -> {
                    // nothing to check
                }

                it is IncrementalMap.Insert -> {
                    var new = HashMap(cache!!.after)
                    assertFalse(new.contains(it.key))
                    new[it.key] = it.after[it.key]
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalMap.Move -> {
                    var new = HashMap(cache!!.after)
                    assertTrue(new.contains(it.key))
                    assertFalse(new.contains(it.outKey))
                    new[it.outKey] = new.remove(it.key)
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalMap.Remove -> {
                    var new = HashMap(cache!!.after)
                    assertTrue(new.contains(it.key))
                    new.remove(it.key)
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                it is IncrementalMap.Update -> {
                    var new = HashMap(cache!!.after)
                    assertTrue(
                        "at \"$tag\", processing op $it: expected key to exist in $new",
                        new.contains(it.key)
                    )
                    new[it.key] = it.after[it.key]
                    assertEquals(
                        "at \"$tag\", expected match while processing op $it (old=${cache!!.after})",
                        it.after,
                        new
                    )
                }

                else -> throw IllegalArgumentException("unknown command?")
            }
            emit(it)
            cache = it
        }
    }
}