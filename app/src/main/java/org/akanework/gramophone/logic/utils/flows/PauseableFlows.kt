package org.akanework.gramophone.logic.utils.flows

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

interface PauseManager : CoroutineContext.Element {
    val isPaused: StateFlow<Boolean>

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<PauseManager>
}

class LifecyclePauseManager(
    scope: CoroutineScope, source: LifecycleOwner, minimumState: Lifecycle.State,
    bypassFlow: Flow<Boolean>
) : PauseManager {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val isPaused = bypassFlow.flatMapLatest {
        if (it) flowOf(false) else
            source.lifecycle.currentStateFlow.map { !it.isAtLeast(minimumState) }
    }
        .stateIn(scope, WhileSubscribed(), !source.lifecycle.currentState.isAtLeast(minimumState))
}

object EmptyPauseManager : PauseManager {
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val isPaused = object : StateFlow<Boolean> {
        override val value: Boolean = false
        override val replayCache = listOf(false)

        override suspend fun collect(collector: FlowCollector<Boolean>): Nothing {
            collector.emit(false)
            while (true)
                delay(Duration.INFINITE)
        }

    }
}

@OptIn(ExperimentalCoroutinesApi::class) // TODO do we want to use a SharingStarted for this at all? we have no replay cache
class CountingPauseManager(paused: SharingStarted) : PauseManager {
    private val flows = MutableStateFlow(listOf<PauseManager>())
    override val isPaused = paused.command(flows.flatMapLatest {
        combine(it.map { it.isPaused }) { it.size - it.count { it } }
    }.stateIn(CoroutineScope(Dispatchers.Default), Eagerly, 0))
        .mapLatest {
            when (it) {
                SharingCommand.START -> false
                SharingCommand.STOP,
                SharingCommand.STOP_AND_RESET_REPLAY_CACHE -> true
            }
        }.stateIn(CoroutineScope(Dispatchers.Default), Eagerly, true)

    fun add(other: PauseManager) {
        flows.value += other
    }

    fun remove(other: PauseManager) {
        flows.value -= other
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PauseManagingSharedFlow<T>(paused: SharingStarted) : SharedFlow<T> {
    private val pauseManager = CountingPauseManager(paused)
    lateinit var sharedFlow: SharedFlow<T>

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val pm = currentCoroutineContext()[PauseManager] ?: EmptyPauseManager
        pauseManager.add(pm)
        try {
            withContext(pauseManager) {
                sharedFlow.collect(collector)
            }
        } finally {
            pauseManager.remove(pm)
        }
    }

    companion object {
        fun <T> Flow<T>.sharePauseableIn(
            scope: CoroutineScope,
            started: SharingStarted,
            paused: SharingStarted = WhileSubscribed(),
            replay: Int = 0
        ): SharedFlow<T> {
            val wrapper = PauseManagingSharedFlow<T>(paused)
            wrapper.sharedFlow = shareIn(
                CoroutineScope(
                    scope.coroutineContext + Job(scope.coroutineContext[Job])
                            + wrapper.pauseManager
                ), started, replay
            )
            return wrapper
        }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PauseManagingStateFlow<T>(paused: SharingStarted) : StateFlow<T> {
    private val pauseManager = CountingPauseManager(paused)
    lateinit var stateFlow: StateFlow<T>

    override val replayCache: List<T>
        get() = stateFlow.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val pm = currentCoroutineContext()[PauseManager] ?: EmptyPauseManager
        pauseManager.add(pm)
        try {
            withContext(pauseManager) {
                stateFlow.collect(collector)
            }
        } finally {
            pauseManager.remove(pm)
        }
    }

    override val value: T
        get() = stateFlow.value

    companion object {
        fun <T> Flow<T>.statePauseableIn(
            scope: CoroutineScope,
            started: SharingStarted,
            paused: SharingStarted = WhileSubscribed(),
            initialValue: T
        ): SharedFlow<T> {
            val wrapper = PauseManagingStateFlow<T>(paused)
            wrapper.stateFlow = stateIn(
                CoroutineScope(
                    scope.coroutineContext + Job(scope.coroutineContext[Job])
                            + wrapper.pauseManager
                ), started, initialValue
            )
            return wrapper
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> repeatFlowWhenUnpaused(block: suspend () -> T): Flow<T> {
    val pauseManager = currentCoroutineContext()[PauseManager]
        ?: throw IllegalStateException("expected to find PauseManager")
    return pauseManager.isPaused.flatMapLatest {
        if (!it)
            flowOf(block())
        else emptyFlow()
    }
}

suspend fun repeatWhenUnpaused(block: suspend () -> Unit) {
    repeatFlowWhenUnpaused(block).collect()
}

suspend fun <T> repeatUntilDoneWhenUnpaused(block: suspend () -> T): T {
    return repeatFlowWhenUnpaused(block).first()
}

fun repeatPausingWithLifecycle(
    source: LifecycleOwner,
    context: CoroutineContext = EmptyCoroutineContext,
    minimumStateForCollect: Lifecycle.State = Lifecycle.State.INITIALIZED,
    minimumStateForUnpause: Lifecycle.State = Lifecycle.State.RESUMED,
    initialUnpauseUntilResumeTimeoutMs: ULong = 2000U,
    block: suspend () -> Unit
) {
    val maxForceUnpausedTimestamp =
        SystemClock.elapsedRealtime() + initialUnpauseUntilResumeTimeoutMs.toLong()
    val bypass = flow {
        val timeLeft = maxForceUnpausedTimestamp - SystemClock.elapsedRealtime()
        if (timeLeft > 0) {
            emit(true)
            delay(timeLeft)
        }
        emit(false)
    }
    source.lifecycleScope.launch(context) {
        if (minimumStateForCollect == Lifecycle.State.INITIALIZED) {
            withContext(
                context + LifecyclePauseManager(
                    this,
                    source,
                    minimumStateForUnpause,
                    bypass
                )
            ) {
                block()
            }
        } else {
            source.repeatOnLifecycle(minimumStateForCollect) {
                withContext(
                    context + LifecyclePauseManager(
                        this,
                        source,
                        minimumStateForUnpause,
                        bypass
                    )
                ) {
                    block()
                }
            }
        }
    }
}

// Downstream will still finish processing values, to avoid, wrap downstream in repeatUntilDoneWhenUnpaused
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.bufferAndBlockWhenPaused(capacity: Int = Channel.UNLIMITED): Flow<T> = channelFlow {
    coroutineScope {
        produce(capacity = capacity) {
            collect { item -> send(item) }
        }.consume {
            repeatWhenUnpaused {
                while (true) {
                    val item = receiveCatching()
                    if (item.isClosed) {
                        close()
                        break
                    }
                    send(item.getOrThrow())
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Flow<T>.conflateAndBlockWhenPaused() = bufferAndBlockWhenPaused(Channel.CONFLATED)