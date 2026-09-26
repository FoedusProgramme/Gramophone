package org.akanework.gramophone.ui.nav

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.akanework.gramophone.ui.components.compose.AppDialogHost
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.screens.HomeScreen
import org.akanework.gramophone.ui.screens.LibrarySubScreen
import org.akanework.gramophone.ui.screens.PlaylistEditScreen
import org.akanework.gramophone.ui.screens.SearchScreen
import org.akanework.gramophone.ui.screens.SongDetailScreen
import org.akanework.gramophone.ui.screens.settings.AboutSettingsScreen
import org.akanework.gramophone.ui.screens.settings.AppearanceSettingsScreen
import org.akanework.gramophone.ui.screens.settings.AudioSettingsScreen
import org.akanework.gramophone.ui.screens.settings.BehaviorSettingsScreen
import org.akanework.gramophone.ui.screens.settings.BlacklistScreen
import org.akanework.gramophone.ui.screens.settings.ContributorsScreen
import org.akanework.gramophone.ui.screens.settings.ExperimentalSettingsScreen
import org.akanework.gramophone.ui.screens.settings.LyricSettingsScreen
import org.akanework.gramophone.ui.screens.settings.MainSettingsScreen
import org.akanework.gramophone.ui.screens.settings.OssLicensesScreen
import org.akanework.gramophone.ui.screens.settings.PlayerSettingsScreen
import org.akanework.gramophone.ui.screens.settings.ReplayGainSettingsScreen
import org.akanework.gramophone.ui.screens.settings.ThemeSettingsScreen

sealed interface AppNavKey : NavKey {
    val wantsPlayer: Boolean
}

data object HomeKey : AppNavKey {
    override val wantsPlayer = true
}

/** The search page, opened with [query] typed in already when it comes from an intent. */
class SearchKey(val query: String?) : AppNavKey {
    override val wantsPlayer = true
}

/** The details of one song. */
class SongDetailKey(val mediaId: String) : AppNavKey {
    override val wantsPlayer = false
}

/** Editing the playlist with MediaStore id [id]. */
class PlaylistEditKey(val id: Long) : AppNavKey {
    override val wantsPlayer = false
}

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

    /** Accent color per back stack entry, used to harmonize the mini player. */
    val pageAccents: SnapshotStateMap<AppNavKey, Color> = mutableStateMapOf()

    /** Accent of the top page, or null if it uses the app colors. */
    val topAccent: Color? get() = backStack.lastOrNull()?.let { pageAccents[it] }
}

/** Bottom padding (px) content should keep clear so the mini player does not cover it. */
val LocalPlayerBottomPadding = compositionLocalOf { 0 }

/** Top padding (dp) content should keep clear so the frosted top bar does not cover it. */
val LocalAppBarTopPadding = compositionLocalOf { 0.dp }

/**
 * Bottom padding (dp) a list keeps clear, or null for the default: the navigation bar or the
 * mini player, whichever is taller. The home's sheet ends above both, so its lists get 0.
 */
val LocalListBottomPadding = compositionLocalOf<Dp?> { null }

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
    dialogs: AppDialogHostState,
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
        AppDialogHost(dialogs)
        // Above the mini player when it shows, else above the navigation bar.
        val snackbarBottom = if (playerBottomPadding > 0) with(LocalDensity.current) { playerBottomPadding.toDp() }
            else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        SnackbarHost(
            hostState = dialogs.snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = snackbarBottom),
        )
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
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    val push: (AppNavKey) -> Unit = { backStack.add(it) }
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
            entry<SearchKey> { key -> SearchScreen(initialQuery = key.query, onBack = pop) }
            entry<SongDetailKey> { key -> SongDetailScreen(mediaId = key.mediaId, onBack = pop) }
            entry<PlaylistEditKey> { key -> PlaylistEditScreen(playlistId = key.id, onBack = pop) }
            entry<MainSettingsKey> { MainSettingsScreen(onBack = pop, onNavigate = push) }
            entry<AppearanceSettingsKey> { AppearanceSettingsScreen(onBack = pop, onNavigate = push) }
            entry<ThemeSettingsKey> { ThemeSettingsScreen(onBack = pop) }
            entry<PlayerSettingsKey> { PlayerSettingsScreen(onBack = pop, onNavigate = push) }
            entry<LyricSettingsKey> { LyricSettingsScreen(onBack = pop) }
            entry<BehaviorSettingsKey> { BehaviorSettingsScreen(onBack = pop, onNavigate = push) }
            entry<AudioSettingsKey> { AudioSettingsScreen(onBack = pop, onNavigate = push) }
            entry<ReplayGainSettingsKey> { ReplayGainSettingsScreen(onBack = pop) }
            entry<ExperimentalSettingsKey> { ExperimentalSettingsScreen(onBack = pop) }
            entry<AboutSettingsKey> { AboutSettingsScreen(onBack = pop, onNavigate = push) }
            entry<BlacklistKey> { BlacklistScreen(onBack = pop) }
            entry<OssLicensesKey> { OssLicensesScreen(onBack = pop) }
            entry<ContributorsKey> { ContributorsScreen(onBack = pop) }
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
    // What the preview reveals around the two containers. Left to the window, it would be the
    // XML theme's surface, resolved once from the system palette and brightness.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
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
