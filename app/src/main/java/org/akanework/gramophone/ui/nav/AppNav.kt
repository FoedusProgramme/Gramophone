package org.akanework.gramophone.ui.nav

import android.os.Parcelable
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.akanework.gramophone.ui.components.compose.AppDialogHost
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.components.compose.LocalAppDialogs
import org.akanework.gramophone.ui.components.player.LocalPlayerSheet
import org.akanework.gramophone.ui.components.player.PlayerSheetController
import org.akanework.gramophone.ui.components.player.PlayerSheetHost
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
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.random.Random

/**
 * A page of the activity. Keys with arguments also carry a random `uid`, saved along with them,
 * that tells two pushes of one page apart: the same album can be on the back stack twice (reached
 * again through its artist, say), and nav3 keeps each entry's state by its key. The settings keys
 * need none, see [SettingsKey].
 */
sealed interface AppNavKey : NavKey {
    val wantsPlayer: Boolean

    /** The page this key opens, without its `uid`: equal for every push of that page. */
    val destination: AppNavKey get() = this
}

/** A new `uid` for a key with arguments, see [AppNavKey]. */
internal fun newPageUid(): Long = Random.nextLong()

@Parcelize
data object HomeKey : AppNavKey, Parcelable {
    @IgnoredOnParcel
    override val wantsPlayer = true
}

/** The search page, opened with [query] typed in already when it comes from an intent. */
@Parcelize
data class SearchKey(val query: String?, val uid: Long = newPageUid()) : AppNavKey, Parcelable {
    @IgnoredOnParcel
    override val wantsPlayer = true
    override val destination: AppNavKey get() = copy(uid = 0)
}

/** The details of one song. */
@Parcelize
data class SongDetailKey(
    val mediaId: String,
    val uid: Long = newPageUid(),
) : AppNavKey, Parcelable {
    @IgnoredOnParcel
    override val wantsPlayer = false
    override val destination: AppNavKey get() = copy(uid = 0)
}

/** Editing the playlist with MediaStore id [id]. */
@Parcelize
data class PlaylistEditKey(val id: Long, val uid: Long = newPageUid()) : AppNavKey, Parcelable {
    @IgnoredOnParcel
    override val wantsPlayer = false
    override val destination: AppNavKey get() = copy(uid = 0)
}

/** A library detail page. */
sealed interface LibrarySubKey : AppNavKey {
    override val wantsPlayer: Boolean get() = true
}

@Parcelize
data class AlbumKey(val id: Long?, val uid: Long = newPageUid()) : LibrarySubKey, Parcelable {
    override val destination: AppNavKey get() = copy(uid = 0)
}

@Parcelize
data class GenreKey(val id: Long?, val uid: Long = newPageUid()) : LibrarySubKey, Parcelable {
    override val destination: AppNavKey get() = copy(uid = 0)
}

@Parcelize
data class DateKey(val id: Long?, val uid: Long = newPageUid()) : LibrarySubKey, Parcelable {
    override val destination: AppNavKey get() = copy(uid = 0)
}

@Parcelize
data class PlaylistKey(
    val id: Long?,
    val className: String?,
    val uid: Long = newPageUid(),
) : LibrarySubKey, Parcelable {
    override val destination: AppNavKey get() = copy(uid = 0)
}

@Parcelize
data class ArtistKey(
    val id: Long?,
    val albumArtist: Boolean,
    val uid: Long = newPageUid(),
) : LibrarySubKey, Parcelable {
    override val destination: AppNavKey get() = copy(uid = 0)
}

/**
 * The activity's pages and their cover color schemes. The pages survive process death too, kept
 * in [handle]; the schemes don't, and each page sets its own again when it comes back.
 */
class NavViewModel(handle: SavedStateHandle) : ViewModel() {
    private val _backStack: SnapshotStateList<AppNavKey> =
        handle.get<SnapshotStateList<AppNavKey>>("backStack")?.takeIf { it.isNotEmpty() }
            ?: mutableStateListOf<AppNavKey>(HomeKey).also { handle["backStack"] = it }
    private val _pageSchemes = mutableStateMapOf<AppNavKey, ColorScheme>()

    /** Destination of each page that has moved off its own, see [setShownDestination]. */
    private val shownDestinations = HashMap<AppNavKey, AppNavKey>()

    /** The open pages, [HomeKey] first and the top page last. Never empty. */
    val backStack: List<AppNavKey> get() = _backStack

    /**
     * Color scheme per back stack entry that is themed from a cover, set with [setPageScheme].
     * The dialogs take the top page's, and the mini player stops harmonizing to the app's hue on
     * top of one.
     */
    val pageSchemes: Map<AppNavKey, ColorScheme> get() = _pageSchemes

    /** Scheme of the top page, or null if it uses the app colors. */
    val topScheme: ColorScheme? get() = backStack.lastOrNull()?.let { _pageSchemes[it] }

    /**
     * Opens [key] on top, unless the top page shows that destination already: a second tap, or
     * "Go to album" on that album's own page, would only stack an identical page.
     */
    fun navigateTo(key: AppNavKey) {
        val top = _backStack.last()
        if ((shownDestinations[top] ?: top.destination) != key.destination) _backStack.add(key)
    }

    /**
     * Closes the page [key] if it is still the top one. Pages close themselves through this, so a
     * late call (from a page still animating out, say) can't close the page that has come on top
     * since. The home is never closed.
     */
    fun popIf(key: AppNavKey) {
        if (_backStack.size > 1 && _backStack.last() == key) {
            _backStack.removeAt(_backStack.lastIndex)
            shownDestinations.remove(key)
        }
    }

    /**
     * Tells that the page [key] now shows [destination] (a detail page switched entries in its
     * carousel), which [navigateTo] compares new pages with while it is on top.
     */
    fun setShownDestination(key: AppNavKey, destination: AppNavKey) {
        if (key in _backStack) shownDestinations[key] = destination.destination
    }

    /** Themes the page [key] with [scheme], or with the app colors again if null. */
    fun setPageScheme(key: AppNavKey, scheme: ColorScheme?) {
        if (scheme != null) _pageSchemes[key] = scheme else _pageSchemes.remove(key)
    }
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

/**
 * Whether a list keeps clear of the system bars and cutouts at its sides. The home's sheet keeps
 * clear of them itself, with its corners, so its lists don't.
 */
val LocalListSideInsets = compositionLocalOf { true }

/** True while a page covers the always-composed home (so it can pause its animations). */
val LocalHomeCovered = compositionLocalOf { false }

/** Reports the app as fully drawn, which also ends the splash screen. No-op by default. */
val LocalReportFullyDrawn = staticCompositionLocalOf<() -> Unit> { {} }

private val HOME_CONTENT_KEY: Any = NavEntry<AppNavKey>(HomeKey, content = {}).contentKey

/**
 * The pages of the activity's [NavViewModel], the dialogs and snackbar, and above them all the
 * player sheet. Provides [dialogs] and [playerSheet] to the pages.
 */
@Composable
fun AppRoot(
    playerSheet: PlayerSheetController,
    dialogs: AppDialogHostState,
    debug: Boolean,
) {
    val navViewModel = koinActivityViewModel<NavViewModel>()
    val top = navViewModel.backStack.lastOrNull()
    LaunchedEffect(top) {
        top?.let { playerSheet.visible = it.wantsPlayer }
    }
    val playerBottomPadding = playerSheet.bottomPadding
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalAppDialogs provides dialogs,
            LocalPlayerSheet provides playerSheet,
        ) {
            CompositionLocalProvider(LocalPlayerBottomPadding provides playerBottomPadding) {
                // Not drawn while the expanded player covers them: the lyrics redraw every frame
                // while they scroll, and would have the pages (and their blurred bars) redrawn
                // underneath each time.
                AppNavHost(
                    navViewModel,
                    Modifier.drawWithContent { if (!playerSheet.coversScreen) drawContent() },
                )
            }
        }
        // A page themed from a cover shows its dialogs in its own colors.
        MaterialTheme(colorScheme = navViewModel.topScheme ?: MaterialTheme.colorScheme) {
            AppDialogHost(dialogs)
        }
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
        // Drawn above the pages, dialogs and snackbar. Composed after the pages so its back
        // callback takes priority over theirs.
        PlayerSheetHost(playerSheet)
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
private fun AppNavHost(navigation: NavViewModel, modifier: Modifier = Modifier) {
    val backStack = navigation.backStack
    val density = LocalDensity.current
    val offset = with(density) { NAV_TRANSITION_DISTANCE.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    // Each page closes only itself, see NavViewModel.popIf.
    val close: (AppNavKey) -> Unit = navigation::popIf
    val push: (AppNavKey) -> Unit = navigation::navigateTo
    val navDisplayState = rememberAndroidPredictiveBackNavDisplayState(
        backStack = backStack,
        onPop = close,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            ExitingPageInputBlocker,
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                // Placeholder: the home itself lives below the NavDisplay, see AppNavHost.
                Box(Modifier.fillMaxSize())
            }
            entry<AlbumKey> { LibrarySubScreen(it, onBack = { close(it) }) }
            entry<GenreKey> { LibrarySubScreen(it, onBack = { close(it) }) }
            entry<DateKey> { LibrarySubScreen(it, onBack = { close(it) }) }
            entry<PlaylistKey> { LibrarySubScreen(it, onBack = { close(it) }) }
            entry<ArtistKey> { LibrarySubScreen(it, onBack = { close(it) }) }
            entry<SearchKey> { SearchScreen(initialQuery = it.query, onBack = { close(it) }) }
            entry<SongDetailKey> {
                SongDetailScreen(mediaId = it.mediaId, onBack = { close(it) })
            }
            entry<PlaylistEditKey> {
                PlaylistEditScreen(playlistId = it.id, onBack = { close(it) })
            }
            entry<MainSettingsKey> { MainSettingsScreen(onBack = { close(it) }, onNavigate = push) }
            entry<AppearanceSettingsKey> {
                AppearanceSettingsScreen(onBack = { close(it) }, onNavigate = push)
            }
            entry<ThemeSettingsKey> { ThemeSettingsScreen(onBack = { close(it) }) }
            entry<PlayerSettingsKey> {
                PlayerSettingsScreen(onBack = { close(it) }, onNavigate = push)
            }
            entry<LyricSettingsKey> { LyricSettingsScreen(onBack = { close(it) }) }
            entry<BehaviorSettingsKey> {
                BehaviorSettingsScreen(onBack = { close(it) }, onNavigate = push)
            }
            entry<AudioSettingsKey> {
                AudioSettingsScreen(onBack = { close(it) }, onNavigate = push)
            }
            entry<ReplayGainSettingsKey> { ReplayGainSettingsScreen(onBack = { close(it) }) }
            entry<ExperimentalSettingsKey> { ExperimentalSettingsScreen(onBack = { close(it) }) }
            entry<AboutSettingsKey> {
                AboutSettingsScreen(onBack = { close(it) }, onNavigate = push)
            }
            entry<BlacklistKey> { BlacklistScreen(onBack = { close(it) }) }
            entry<OssLicensesKey> { OssLicensesScreen(onBack = { close(it) }) }
            entry<ContributorsKey> { ContributorsScreen(onBack = { close(it) }) }
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
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
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
                )
                // Until a committed gesture has popped, the page it closes still takes the
                // touches where it was, though it's only drawn flying off, and the page it
                // reveals isn't on top yet.
                .then(
                    if (visualState.phase == PredictiveBackPhase.Committing) {
                        Modifier.blockPointerInput()
                    } else {
                        Modifier
                    }
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
    if (visualState.isActive() && !homeIsPrevious &&
        navDisplayState.sceneState.canShowPredictivePreview()
    ) {
        AndroidPredictiveBackPreview(
            state = visualState,
            sceneState = navDisplayState.sceneState,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val suppressPop = visualState.suppressNextPopTransition
        CompositionLocalProvider(LocalInNavDisplay provides true) {
            NavDisplay(
                sceneState = navDisplayState.sceneState,
                navigationEventState = navDisplayState.navigationEventState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { navOpenTransition(horizontalOffset) },
                popTransitionSpec = { navPopTransition(suppressPop, horizontalOffset) },
            )
        }
    }
}

/**
 * Whether the entry content is composed by NavDisplay, which provides
 * [LocalNavAnimatedContentScope]. The predictive back preview composes entries without it.
 */
private val LocalInNavDisplay = staticCompositionLocalOf { false }

/**
 * Swallows the touches on a page NavDisplay is animating out. A closing page stays above the one
 * it reveals for the whole close transition, even once it has faded out, and would take the taps
 * meant for that page.
 */
private val ExitingPageInputBlocker = NavEntryDecorator<AppNavKey> { entry ->
    val exiting = if (LocalInNavDisplay.current) {
        LocalNavAnimatedContentScope.current.transition.targetState == EnterExitState.PostExit
    } else {
        false
    }
    // The same Box either way, so the page isn't recreated when it moves into the preview.
    Box(
        if (exiting) Modifier.blockPointerInput() else Modifier,
        propagateMinConstraints = true,
    ) {
        entry.Content()
    }
}

/** Takes every touch within the bounds before anything inside sees it. */
private fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
