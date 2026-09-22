package org.akanework.gramophone.ui.nav

import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.akanework.gramophone.ui.fragments.SettingsPageFragment
import org.akanework.gramophone.ui.fragments.settings.BlacklistHostFragment
import org.akanework.gramophone.ui.fragments.settings.ContributorsScreen
import org.akanework.gramophone.ui.fragments.settings.OssLicensesScreen
import org.akanework.gramophone.ui.screens.HomeScreen
import org.akanework.gramophone.ui.screens.LibrarySubScreen

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

/** A library detail page. Plain classes, so the same page can be on the back stack twice. */
sealed interface LibrarySubKey : AppNavKey {
    override val wantsPlayer: Boolean get() = true
}

class AlbumKey(val id: Long?) : LibrarySubKey
class GenreKey(val id: Long?) : LibrarySubKey
class DateKey(val id: Long?) : LibrarySubKey
class PlaylistKey(val id: Long?, val className: String?) : LibrarySubKey
class ArtistKey(val id: Long?, val albumArtist: Boolean) : LibrarySubKey

class NavViewModel : ViewModel() {
    val backStack: SnapshotStateList<AppNavKey> = mutableStateListOf(HomeKey)
}

/** Bottom padding (px) content should keep clear so the mini player does not cover it. */
val LocalPlayerBottomPadding = compositionLocalOf { 0 }

fun SnapshotStateList<AppNavKey>.popIfPossible() {
    if (size > 1) removeAt(size - 1)
}

/** True while a page covers the always-composed home (so it can pause its animations). */
val LocalHomeCovered = compositionLocalOf { false }

private val HOME_CONTENT_KEY: Any = NavEntry<AppNavKey>(HomeKey, content = {}).contentKey

@Composable
fun AppRoot(
    backStack: SnapshotStateList<AppNavKey>,
    onPlayerVisibleChanged: (Boolean) -> Unit,
    playerBottomPadding: Int,
    debug: Boolean,
) {
    val top = backStack.lastOrNull()
    LaunchedEffect(top) {
        top?.let { onPlayerVisibleChanged(it.wantsPlayer) }
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPlayerBottomPadding provides playerBottomPadding) {
            AppNavHost(backStack)
        }
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

/**
 * The home is composed once and kept underneath the NavDisplay instead of being a real entry,
 * which nav3 would dispose whenever a page is pushed and rebuild on every back gesture. Its
 * NavDisplay entry is a transparent placeholder. The home container plays the "previous page"
 * role of the predictive back preview when a gesture reveals it, and slides 96dp like the
 * shared-axis transition of a real entry when pages are pushed or popped.
 */
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
                // Placeholder: the home itself lives below the NavDisplay, see AppNavHost.
                Box(Modifier.fillMaxSize())
            }
            entry<AlbumKey> { LibrarySubScreen(it, onBack = { backStack.removeLastOrNull() }) }
            entry<GenreKey> { LibrarySubScreen(it, onBack = { backStack.removeLastOrNull() }) }
            entry<DateKey> { LibrarySubScreen(it, onBack = { backStack.removeLastOrNull() }) }
            entry<PlaylistKey> { LibrarySubScreen(it, onBack = { backStack.removeLastOrNull() }) }
            entry<ArtistKey> { LibrarySubScreen(it, onBack = { backStack.removeLastOrNull() }) }
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
    val visualState = navDisplayState.visualState
    val previousEntry = navDisplayState.sceneState.previousScenes.lastOrNull()?.entries?.lastOrNull()
    val homeIsPrevious = previousEntry?.contentKey == HOME_CONTENT_KEY
    // Back to the home: nothing is moved. The home container plays the "previous page" role
    // and the NavDisplay container the "current page" role of the Android-style preview.
    val inlinePreview = visualState.isActive() && homeIsPrevious
    val covered = backStack.size > 1
    // Shared-axis motion of the home, in step with NavDisplay's push / pop transition.
    val homeOffset = remember { Animatable(0f) }
    LaunchedEffect(covered) {
        val target = if (covered) -offset.toFloat() else 0f
        if (!covered && visualState.suppressNextPopTransition) {
            // The predictive preview already brought the home back into place.
            homeOffset.snapTo(0f)
        } else {
            homeOffset.animateTo(target, tween(NAV_TRANSITION_MS, easing = NavAxisEasing))
        }
    }
    // Read in the draw phase. Right after a predictive commit the animatable still holds the
    // covered offset until the effect above has run, which would shift the home for a frame.
    val homeIdleTranslation = {
        if (!covered && visualState.suppressNextPopTransition) 0f else homeOffset.value
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .predictiveBackRole(
                    visualState, PredictiveBackRole.Previous,
                    enabled = inlinePreview,
                    idleTranslationX = homeIdleTranslation,
                ),
        ) {
            CompositionLocalProvider(LocalHomeCovered provides covered) {
                HomeScreen(modifier = Modifier.fillMaxSize())
            }
        }
        if (inlinePreview) AndroidPredictiveBackScrim(visualState)
        Box(
            Modifier
                .fillMaxSize()
                .predictiveBackRoleDrawn(
                    visualState, PredictiveBackRole.Current,
                    enabled = inlinePreview,
                    // NavDisplay still shows the popped page for one frame after a predictive
                    // commit, until its (silent) pop transition has run.
                    hidden = { visualState.suppressNextPopTransition && backStack.size == 1 },
                ),
        ) {
            AndroidPredictiveBackNavigationScene(
                navDisplayState = navDisplayState,
                horizontalOffset = offset,
                homeIsPrevious = homeIsPrevious,
            )
        }
    }
}

/**
 * While a predictive back gesture is past its reveal threshold and the page underneath is a
 * real entry, the two topmost entries are drawn by [AndroidPredictiveBackPreview]. Otherwise
 * NavDisplay renders them with the shared-axis open and close transitions. With the home
 * underneath, [AppNavHost] draws the gesture on the containers instead.
 */
@Composable
private fun AndroidPredictiveBackNavigationScene(
    navDisplayState: AndroidPredictiveBackNavDisplayState<AppNavKey>,
    horizontalOffset: Int,
    homeIsPrevious: Boolean,
) {
    val visualState = navDisplayState.visualState
    LaunchedEffect(visualState.suppressNextPopTransition) {
        if (visualState.suppressNextPopTransition) {
            withFrameNanos {}
            withFrameNanos {}
            visualState.clearPopTransitionSuppression()
        }
    }
    if (visualState.isActive() && !homeIsPrevious) {
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
        )
    }
}
