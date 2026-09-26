package org.akanework.gramophone.ui.nav

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState.Idle
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Drives the Android-style cross-activity predictive back preview: tracks the gesture while the
 * finger drags, then plays either a spring-back cancel or a post-commit settle and fling.
 *
 * Adapted from tuned (ink.duo3.tuned.navigation.AndroidPredictiveBackState).
 */
@Stable
internal class AndroidPredictiveBackState {
    private var settleJob: Job? = null
    private var flingJob: Job? = null
    private var lastFrameTimeMillis = 0L
    private var lastGestureProgress = 0f
    private var gestureVelocity = 0f

    var phase by mutableStateOf(PredictiveBackPhase.Idle)
        private set

    var gestureProgress by mutableFloatStateOf(0f)
        private set

    var commitProgress by mutableFloatStateOf(0f)
        private set

    var touchDeltaY by mutableFloatStateOf(0f)
        private set

    var postCommitFlingScale by mutableFloatStateOf(1f)
        private set

    var suppressNextPopTransition by mutableStateOf(false)
        private set

    var swipeEdge by mutableIntStateOf(NavigationEvent.EDGE_LEFT)
        private set

    private var initialTouchY = 0f

    fun updateGesture(event: NavigationEvent) {
        // A terminal animation (commit or cancel) is already running. The navigation-event
        // transition state lags a frame behind the synchronous terminal callback, so a stale
        // `InProgress` can arrive here *after* `commit`/`cancel`. Ignore it: reverting to Dragging
        // would cancel the pending pop and strand the gesture (no back ever happens).
        if (phase == PredictiveBackPhase.Committing || phase == PredictiveBackPhase.Canceling) return
        val progress = BackGestureEasing.transform(event.progress)
        // A flick too small to ever reveal the predictive preview must not engage it: keeping the
        // phase Idle lets `onBackCompleted` fall through to NavDisplay's normal pop animation
        // instead of the predictive commit transition.
        if (phase != PredictiveBackPhase.Dragging && progress < MIN_PREVIEW_PROGRESS) return
        settleJob?.cancel()
        if (phase != PredictiveBackPhase.Dragging) {
            initialTouchY = event.touchY
            lastFrameTimeMillis = event.frameTimeMillis
            lastGestureProgress = progress
            gestureVelocity = 0f
        }
        updateGestureVelocity(progress, event.frameTimeMillis)
        phase = PredictiveBackPhase.Dragging
        swipeEdge = event.swipeEdge
        gestureProgress = progress
        touchDeltaY = event.touchY - initialTouchY
    }

    fun recoverDanglingGesture(scope: CoroutineScope) {
        if (phase == PredictiveBackPhase.Dragging) cancel(scope)
    }

    fun cancel(scope: CoroutineScope) {
        settleJob?.cancel()
        flingJob?.cancel()
        phase = PredictiveBackPhase.Canceling
        settleJob = scope.launch {
            animate(
                initialValue = gestureProgress,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 1600f),
            ) { value, _ ->
                gestureProgress = value
            }
            clear()
        }
    }

    fun commit(scope: CoroutineScope, onCommitted: () -> Unit) {
        settleJob?.cancel()
        flingJob?.cancel()
        phase = PredictiveBackPhase.Committing
        commitProgress = 0f
        flingJob = scope.launch {
            animate(
                initialValue = 1f,
                targetValue = 1f,
                initialVelocity = calculatePostCommitFlingVelocity(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ) { value, _ ->
                postCommitFlingScale = min(value, 1f)
            }
        }
        settleJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(POST_COMMIT_DURATION_MILLIS, easing = LinearEasing),
            ) { value, _ ->
                commitProgress = value
            }
            suppressNextPopTransition = true
            onCommitted()
            clear()
        }
    }

    fun isActive(): Boolean = phase != PredictiveBackPhase.Idle

    fun clearPopTransitionSuppression() {
        suppressNextPopTransition = false
    }

    private fun updateGestureVelocity(progress: Float, frameTimeMillis: Long) {
        val elapsedMillis = frameTimeMillis - lastFrameTimeMillis
        if (elapsedMillis > 0) {
            gestureVelocity = (progress - lastGestureProgress) * MILLIS_PER_SECOND / elapsedMillis
        }
        lastFrameTimeMillis = frameTimeMillis
        lastGestureProgress = progress
    }

    private fun calculatePostCommitFlingVelocity(): Float {
        val velocity = gestureVelocity *
            (1f - PREDICTIVE_BACK_MAX_SCALE) *
            POST_COMMIT_FLING_VELOCITY_FACTOR
        val minimumVelocity =
            if (gestureProgress < MIN_GESTURE_PROGRESS_FOR_FLING) DEFAULT_FLING_VELOCITY else 0f
        return -velocity.coerceIn(minimumVelocity, MAX_FLING_VELOCITY)
    }

    private fun clear() {
        flingJob?.cancel()
        flingJob = null
        phase = PredictiveBackPhase.Idle
        gestureProgress = 0f
        commitProgress = 0f
        touchDeltaY = 0f
        postCommitFlingScale = 1f
        lastFrameTimeMillis = 0L
        lastGestureProgress = 0f
        gestureVelocity = 0f
        settleJob = null
    }
}

internal data class AndroidPredictiveBackNavDisplayState<T : Any>(
    val sceneState: SceneState<T>,
    val navigationEventState: NavigationEventState<SceneInfo<T>>,
    val visualState: AndroidPredictiveBackState,
)

/**
 * Builds the [SceneState] / [NavigationEventState] pair that [androidx.navigation3.ui.NavDisplay]
 * consumes, but owns the back handler itself so a predictive gesture can be routed to
 * [AndroidPredictiveBackPreview] instead of NavDisplay's built-in seekable transition. The state
 * handed to NavDisplay never reports a gesture, so NavDisplay only plays its plain push and pop
 * transitions: a flick too short for the preview pops like a button press, and a pop right after
 * the preview is silenced through [AndroidPredictiveBackState.suppressNextPopTransition].
 */
@Composable
internal fun <T : Any> rememberAndroidPredictiveBackNavDisplayState(
    backStack: List<T>,
    onBack: () -> Unit,
    entryProvider: (key: T) -> NavEntry<T>,
    entryDecorators: List<NavEntryDecorator<T>>,
): AndroidPredictiveBackNavDisplayState<T> {
    val visualState = remember { AndroidPredictiveBackState() }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
    )
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
        onBack = onBack,
    )
    val scene = sceneState.currentScene
    val gestureEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(scene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) },
    )
    // Not driven by any handler, so NavDisplay never sees a gesture and never seeks on its own.
    val navigationEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(scene),
        backInfo = emptyList(),
    )
    val coroutineScope = rememberCoroutineScope()
    val gestureTransition = gestureEventState.transitionState
    LaunchedEffect(gestureTransition) {
        when (val transition = gestureTransition) {
            is InProgress -> visualState.updateGesture(transition.latestEvent)
            Idle -> visualState.recoverDanglingGesture(coroutineScope)
        }
    }
    NavigationBackHandler(
        state = gestureEventState,
        isBackEnabled = scene.previousEntries.isNotEmpty(),
        onBackCancelled = { visualState.cancel(coroutineScope) },
        onBackCompleted = {
            val popCount = (entries.size - scene.previousEntries.size).coerceAtLeast(0)
            when (visualState.phase) {
                // A predictive preview is on screen: finish it with the post-commit fling and
                // suppress NavDisplay's own pop transition (the preview already animated the swap).
                PredictiveBackPhase.Dragging -> visualState.commit(coroutineScope) {
                    repeat(popCount) { onBack() }
                }
                // The previous gesture is still settling and will pop when it lands. A second
                // back arriving in that window must not pop twice.
                PredictiveBackPhase.Committing -> Unit
                // No preview was shown (button back, a flick below MIN_PREVIEW_PROGRESS, or a
                // cancel still springing back): pop directly so NavDisplay plays its normal
                // close transition.
                PredictiveBackPhase.Idle, PredictiveBackPhase.Canceling ->
                    repeat(popCount) { onBack() }
            }
        },
    )
    return AndroidPredictiveBackNavDisplayState(sceneState, navigationEventState, visualState)
}

internal enum class PredictiveBackPhase {
    Idle,
    Dragging,
    Canceling,
    Committing,
}

private val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
internal const val PREDICTIVE_BACK_MAX_SCALE = 0.9f
private const val POST_COMMIT_DURATION_MILLIS = 450
private const val POST_COMMIT_FLING_VELOCITY_FACTOR = 2f
private const val MIN_PREVIEW_PROGRESS = 0.05f
private const val MIN_GESTURE_PROGRESS_FOR_FLING = 0.1f
private const val DEFAULT_FLING_VELOCITY = 1.2f
private const val MAX_FLING_VELOCITY = 10f
private const val MILLIS_PER_SECOND = 1000f
