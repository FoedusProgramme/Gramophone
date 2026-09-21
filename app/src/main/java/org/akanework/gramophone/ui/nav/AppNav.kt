package org.akanework.gramophone.ui.nav

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.akanework.gramophone.ui.fragments.SettingsPageFragment
import org.akanework.gramophone.ui.fragments.ViewPagerFragment
import org.akanework.gramophone.ui.fragments.settings.BlacklistHostFragment
import org.akanework.gramophone.ui.fragments.settings.ContributorsScreen
import org.akanework.gramophone.ui.fragments.settings.OssLicensesScreen

sealed interface AppNavKey : NavKey {
    val wantsPlayer: Boolean
}

data object HomeKey : AppNavKey {
    override val wantsPlayer = true
}

class FragmentKey(
    val className: String,
    val args: Bundle?,
    override val wantsPlayer: Boolean,
) : AppNavKey

class NavViewModel : ViewModel() {
    val backStack: SnapshotStateList<AppNavKey> = mutableStateListOf(HomeKey)
}

fun SnapshotStateList<AppNavKey>.popIfPossible() {
    if (size > 1) removeAt(size - 1)
}

@Composable
fun AppRoot(
    backStack: SnapshotStateList<AppNavKey>,
    onPlayerVisibleChanged: (Boolean) -> Unit,
    debug: Boolean,
) {
    val top = backStack.lastOrNull()
    LaunchedEffect(top) {
        top?.let { onPlayerVisibleChanged(it.wantsPlayer) }
    }
    Box(Modifier.fillMaxSize()) {
        AppNavHost(backStack)
        if (debug) {
            Text(
                "DEBUG",
                color = Color.Red,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun AppNavHost(backStack: SnapshotStateList<AppNavKey>) {
    val density = LocalDensity.current
    val offset = with(density) { NAV_TRANSITION_DISTANCE.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val navDisplayState = rememberAndroidPredictiveBackNavDisplayState(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<HomeKey> {
                AndroidFragment<ViewPagerFragment>(
                    modifier = Modifier.fillMaxSize(),
                    fragmentState = rememberFragmentState(),
                )
            }
            entry<FragmentKey> { key ->
                val clazz = remember(key.className) {
                    @Suppress("UNCHECKED_CAST")
                    Class.forName(key.className) as Class<Fragment>
                }
                AndroidFragment(
                    clazz = clazz,
                    modifier = Modifier.fillMaxSize(),
                    fragmentState = rememberFragmentState(),
                    arguments = key.args ?: Bundle.EMPTY,
                )
            }
            entry<SettingsKey> { key ->
                AndroidFragment<SettingsPageFragment>(
                    modifier = Modifier.fillMaxSize(),
                    fragmentState = rememberFragmentState(),
                    arguments = bundleOf(
                        SettingsPageFragment.ARG_TITLE to key.titleRes,
                        SettingsPageFragment.ARG_FRAGMENT to key.fragmentClassName,
                    ),
                )
            }
            entry<BlacklistKey> {
                AndroidFragment<BlacklistHostFragment>(
                    modifier = Modifier.fillMaxSize(),
                    fragmentState = rememberFragmentState(),
                )
            }
            entry<OssLicensesKey> {
                OssLicensesScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<ContributorsKey> {
                ContributorsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
    AndroidPredictiveBackNavigationScene(navDisplayState, offset)
}

/**
 * While a predictive back gesture is past its reveal threshold the two topmost entries are drawn
 * by [AndroidPredictiveBackPreview]. Otherwise NavDisplay renders them with the shared-axis
 * open/close transitions.
 */
@Composable
private fun AndroidPredictiveBackNavigationScene(
    navDisplayState: AndroidPredictiveBackNavDisplayState<AppNavKey>,
    horizontalOffset: Int,
) {
    val visualState = navDisplayState.visualState
    LaunchedEffect(visualState.suppressNextPopTransition) {
        if (visualState.suppressNextPopTransition) {
            withFrameNanos {}
            withFrameNanos {}
            visualState.clearPopTransitionSuppression()
        }
    }
    if (visualState.isActive()) {
        AndroidPredictiveBackPreview(
            state = visualState,
            sceneState = navDisplayState.sceneState,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val suppressPop = visualState.suppressNextPopTransition
        NavDisplay(
            sceneState = navDisplayState.sceneState,
            navigationEventState = navDisplayState.navigationEventState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { navOpenTransition(horizontalOffset) },
            popTransitionSpec = { navPopTransition(suppressPop, horizontalOffset) },
            predictivePopTransitionSpec = { navPopTransition(suppressPop, horizontalOffset) },
        )
    }
}
