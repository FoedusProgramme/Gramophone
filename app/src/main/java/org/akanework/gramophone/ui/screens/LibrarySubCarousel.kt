/*
 *     Copyright (C) 2025 Akane Foundation
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

package org.akanework.gramophone.ui.screens

import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryCover
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.DateKey
import org.akanework.gramophone.ui.nav.GenreKey
import org.akanework.gramophone.ui.nav.LibrarySubKey
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import uk.akane.libphonograph.reader.FlowReader

/*
 * Carousel at the top of a detail page, listing all entries of the page's kind. Selecting a card
 * switches the page to that entry. It is part of the scrolling content.
 */

/** Card height as a fraction of the screen height. */
private const val CAROUSEL_CARD_HEIGHT_SHARE = 0.30f
private val CAROUSEL_CARD_HEIGHT_MIN = 140.dp
private val CAROUSEL_CARD_HEIGHT_MAX = 280.dp

/** Card aspect ratio (width / height). */
private const val CAROUSEL_CARD_ASPECT = 1.4f

/** Width of the collapsed small item at the carousel's edge. */
private val CAROUSEL_PEEK_WIDTH = CarouselDefaults.MaxSmallItemSize

/** Padding around and between the cards. */
internal val CAROUSEL_ROW_PADDING = 8.dp
private val CAROUSEL_ITEM_GAP = 8.dp
private val CAROUSEL_ITEM_CORNER = 28.dp

/** Side margins. The start margin matches the page's 24dp content margin. */
private val CAROUSEL_START_PADDING = 24.dp
private val CAROUSEL_END_PADDING = 16.dp

/** Gap between the status bar and the cards, and the scroll distance over which the toolbar
 *  blur fades in. */
internal val CAROUSEL_TOP_GAP = 4.dp
internal val CAROUSEL_FROST_SPAN = 24.dp

/** Inset of the buttons from the focused card's corners, and their size. */
private val CAROUSEL_BUTTON_INSET = 16.dp
private val CAROUSEL_BUTTON_SIZE = 48.dp

/** Toolbar button padding, where the carousel buttons end up when docked. */
internal val TOOLBAR_BUTTON_PADDING_START = 4.dp
internal val TOOLBAR_BUTTON_PADDING_END = 8.dp

/** Start padding of the toolbar title, leaving room for the docked back button. */
internal val TOOLBAR_TITLE_PADDING = TOOLBAR_BUTTON_PADDING_START + CAROUSEL_BUTTON_SIZE + 4.dp

/** Card height: a fraction of the screen height, clamped for very short or tall screens. */
@Composable
private fun carouselCardHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp.dp * CAROUSEL_CARD_HEIGHT_SHARE)
        .coerceIn(CAROUSEL_CARD_HEIGHT_MIN, CAROUSEL_CARD_HEIGHT_MAX)

/**
 * Width of the focused card: its height times [CAROUSEL_CARD_ASPECT], capped so the collapsed
 * card still fits. Using exactly this as the preferred width makes the multi-browse carousel
 * show one large and one small item instead of several equal ones.
 */
@Composable
private fun carouselCardWidth(): Dp {
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val available = windowWidth - CAROUSEL_START_PADDING - CAROUSEL_END_PADDING
    return (carouselCardHeight() * CAROUSEL_CARD_ASPECT)
        .coerceAtMost(available - CAROUSEL_PEEK_WIDTH - CAROUSEL_ITEM_GAP)
}

/** Height of the carousel, including the vertical padding. */
@Composable
fun subCarouselHeight(): Dp = carouselCardHeight() + CAROUSEL_ROW_PADDING * 2

/**
 * A carousel button. Drawn on a surface-colored circle while on the card, which fades out as the
 * button moves into the toolbar.
 */
@Composable
private fun CarouselButton(
    icon: ImageVector,
    restX: Dp,
    dockX: Dp,
    restY: Dp,
    travelPx: Float,
    scrolled: () -> Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    fun fraction() = (scrolled() / travelPx).coerceIn(0f, 1f)
    Box(
        Modifier
            // Read in the layout and draw phases to avoid recomposing on every scroll frame.
            .offset {
                // Follows the card, including overscroll. Only upward scrolling moves it towards
                // the toolbar position.
                val ridden = scrolled().coerceAtMost(travelPx)
                IntOffset(
                    x = with(density) { lerp(restX, dockX, fraction()).roundToPx() },
                    y = with(density) { (restY - ridden.toDp()).roundToPx() },
                )
            }
            .clip(CircleShape)
            .drawBehind { drawCircle(color = surface.copy(alpha = 1f - fraction())) },
    ) {
        LibraryIconButton(
            icon = icon,
            iconSize = 24.dp,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onClick,
        )
    }
}

/**
 * The back and edit buttons. At rest they sit on the corners of the focused card and follow it
 * when overscrolled. As the card scrolls under the toolbar they move to the toolbar button
 * positions and their background fades out. [scrolled] is the card's scroll offset from rest in
 * px (negative when overscrolled) and [topInset] the status bar height.
 */
@Composable
internal fun CarouselButtons(
    scrolled: () -> Float,
    topInset: Dp,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val cardWidth = carouselCardWidth()
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val horizontalInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .asPaddingValues()
    val direction = LocalLayoutDirection.current
    val insetStart = horizontalInsets.calculateStartPadding(direction)
    val insetEnd = horizontalInsets.calculateEndPadding(direction)
    // Vertical distance from the button position on the card to the toolbar position.
    val restY = topInset + CAROUSEL_TOP_GAP + CAROUSEL_ROW_PADDING + CAROUSEL_BUTTON_INSET
    val dockY = topInset + (GLASS_BAR_HEIGHT - CAROUSEL_BUTTON_SIZE) / 2
    val travelPx = with(density) { (restY - dockY).toPx() }
    Box(Modifier.fillMaxSize()) {
        CarouselButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            restX = CAROUSEL_START_PADDING + CAROUSEL_BUTTON_INSET,
            dockX = insetStart + TOOLBAR_BUTTON_PADDING_START,
            restY = restY,
            travelPx = travelPx,
            scrolled = scrolled,
            onClick = onBack,
        )
        if (onEdit != null) {
            CarouselButton(
                icon = Icons.Outlined.Edit,
                restX = CAROUSEL_START_PADDING + cardWidth - CAROUSEL_BUTTON_INSET -
                        CAROUSEL_BUTTON_SIZE,
                dockX = windowWidth - insetEnd - TOOLBAR_BUTTON_PADDING_END - CAROUSEL_BUTTON_SIZE,
                restY = restY,
                travelPx = travelPx,
                scrolled = scrolled,
                onClick = onEdit,
            )
        }
    }
}

/** A carousel card showing only the entry's cover. The title is shown by the page. */
@Composable
private fun <T : Any> CarouselCard(
    state: LibraryTabState<T>,
    item: T,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LibraryCover(
        uri = state.spec.coverOf(context, item),
        defaultCover = state.spec.defaultCoverOf(item),
        cornerRadius = CAROUSEL_ITEM_CORNER,
        modifier = modifier
            .fillMaxSize()
            // The library's rows and cards are clickable without a ripple.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

/**
 * The detail page carousel, listing all entries of the page's kind (e.g. all albums on an album
 * page). It is the first item of the scrolling content. The focused card is the shown entry, and
 * the screen observes [carouselState] to switch to the card it settles on.
 */
@Composable
private fun <T : Any> SubCarousel(state: LibraryTabState<T>, carouselState: CarouselState) {
    val scope = rememberCoroutineScope()
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = carouselCardWidth(),
        modifier = Modifier
            .fillMaxWidth()
            // Side margins go on the container, not the content padding. M3 only applies content
            // padding at the ends of the list, so middle cards would touch the screen edge.
            .padding(start = CAROUSEL_START_PADDING, end = CAROUSEL_END_PADDING)
            .height(subCarouselHeight()),
        itemSpacing = CAROUSEL_ITEM_GAP,
        // Fixed small item width, matching what carouselCardWidth() assumes.
        minSmallItemWidth = CAROUSEL_PEEK_WIDTH,
        maxSmallItemWidth = CAROUSEL_PEEK_WIDTH,
        contentPadding = PaddingValues(
            top = CAROUSEL_ROW_PADDING,
            bottom = CAROUSEL_ROW_PADDING,
        ),
    ) { index ->
        state.items.getOrNull(index)?.let { item ->
            CarouselCard(
                state = state,
                item = item,
                // Tapping only scrolls the card into focus. The page switches when the carousel
                // settles.
                onClick = { scope.launch { carouselState.animateScrollToItem(index) } },
                // maskClip rounds the visible part of the card, including collapsed ones.
                modifier = Modifier.maskClip(RoundedCornerShape(CAROUSEL_ITEM_CORNER)),
            )
        }
    }
}

/**
 * Entries shown in a detail page's carousel: all entries of the page's kind, sorted like the
 * corresponding tab. Created once per page, so switching entries does not rebuild the list.
 */
internal class SiblingEntries<T : Any>(
    val state: LibraryTabState<T>,
    private val keyOf: (T) -> LibrarySubKey,
) {
    val itemCount: Int get() = state.items.size

    /** Key of the entry at [index], or null before the list has loaded. */
    fun keyAt(index: Int): LibrarySubKey? = state.items.getOrNull(index)?.let(keyOf)

    /** Index of [key] in the list, or -1 if it is missing or the list has not loaded. */
    fun indexOf(key: LibrarySubKey): Int = state.items.indexOfFirst { sameEntry(keyOf(it), key) }

    /** Subtitle of the entry at [index], or null before the list has loaded. */
    @Composable
    fun subtitleAt(index: Int): String? =
        state.items.getOrNull(index)?.let { libraryItemSubtitle(state, it) }

    /** Cover of the entry at [index], or null if there is none or the list has not loaded. */
    @Composable
    fun coverAt(index: Int): Uri? {
        val context = LocalContext.current
        return state.items.getOrNull(index)?.let { state.spec.coverOf(context, it) }
    }

    @Composable
    fun Collect() = CollectLibraryItems(state)

    /** The carousel for this list, driven by [carouselState]. */
    @Composable
    fun Carousel(carouselState: CarouselState) = SubCarousel(state, carouselState)
}

/**
 * True when two keys refer to the same entry. The key classes are not data classes, so the same
 * page can be on the back stack twice. This compares their contents instead.
 */
private fun sameEntry(a: LibrarySubKey, b: LibrarySubKey): Boolean = entryToken(a) == entryToken(b)

/** Unique string for an entry, used to compare keys and as a remember key. */
internal fun entryToken(key: LibrarySubKey): String = when (key) {
    is AlbumKey -> "album:${key.id}"
    is GenreKey -> "genre:${key.id}"
    is DateKey -> "date:${key.id}"
    is PlaylistKey -> "playlist:${key.id}:${key.className}"
    is ArtistKey -> "artist:${key.id}:${key.albumArtist}"
}

/**
 * Carousel list for the page opened by [key]: all entries of the key's kind, in the order of
 * that kind's tab.
 */
internal fun siblingEntries(
    key: LibrarySubKey,
    reader: FlowReader,
    prefs: SharedPreferences,
    scope: CoroutineScope,
): SiblingEntries<*> = when (key) {
    is AlbumKey -> SiblingEntries(LibraryTabState(LibraryTabSpec.Albums, prefs, reader, scope)) {
        AlbumKey(it.id)
    }
    is GenreKey -> SiblingEntries(LibraryTabState(LibraryTabSpec.Genres, prefs, reader, scope)) {
        GenreKey(it.id)
    }
    is DateKey -> SiblingEntries(LibraryTabState(LibraryTabSpec.Dates, prefs, reader, scope)) {
        DateKey(it.id)
    }
    is PlaylistKey -> SiblingEntries(
        LibraryTabState(LibraryTabSpec.Playlists, prefs, reader, scope),
    ) { PlaylistKey(it.id, it.javaClass.name) }
    // The artists tab picks its list from a preference. The key already says which list.
    is ArtistKey -> SiblingEntries(
        LibraryTabState(
            spec = LibraryTabSpec.Artists,
            prefs = prefs,
            reader = reader,
            scope = scope,
            flowOverride = if (key.albumArtist)
                reader.albumArtistListFlow else reader.artistListFlow,
        ),
    ) { ArtistKey(it.id, key.albumArtist) }
}
