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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.akanework.gramophone.ui.components.compose.rememberDefaultPreferences
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import org.akanework.gramophone.ui.components.home.glassHazeStyle
import org.akanework.gramophone.ui.components.home.rememberIosOverscrollState
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.components.home.textViewStyle
import org.akanework.gramophone.ui.components.home.topEdgeBlur
import org.akanework.gramophone.ui.library.Sorter
import org.akanework.gramophone.ui.nav.LocalAppBarTopPadding
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import org.koin.compose.koinInject
import uk.akane.libphonograph.reader.FlowReader

/*
 * The search page: a text field in the glass bar, and under it the song list filtered by what
 * is typed, matching on title, album and artist.
 */

private val FIELD_TEXT_SIZE = 18.sp

@Composable
fun SearchScreen(initialQuery: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reader = koinInject<FlowReader>()
    val prefs = rememberDefaultPreferences()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    val queryFlow = remember { MutableStateFlow(query) }
    LaunchedEffect(query) { queryFlow.value = query }
    val state = remember {
        val songs = reader.songListFlow.combine(queryFlow) { list, raw ->
            val text = raw.trim()
            list.filter {
                it.mediaMetadata.title?.contains(text, true) == true ||
                    it.mediaMetadata.albumTitle?.contains(text, true) == true ||
                    it.mediaMetadata.artist?.contains(text, true) == true
            }
        }
        LibraryTabState(
            LibraryTabSpec.SubSongs(LibraryAdapterTypes.SEARCH, Sorter.Type.ByTitleAscending),
            prefs, reader, scope, flowOverride = songs,
        )
    }
    val queueTitle = stringResource(R.string.search_query, query)
    LaunchedEffect(queueTitle) { state.queueTitleOverride = queueTitle }
    val nowPlaying = rememberNowPlayingState(
        koinActivityViewModel<MediaControllerViewModel>(), LocalLifecycleOwner.current.lifecycle
    )
    val overscroll = rememberIosOverscrollState()
    val hazeState = remember { HazeState() }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val topInset = insets.asPaddingValues().calculateTopPadding()
    val barTopPadding = topInset + GLASS_BAR_HEIGHT
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(background)) {
            CompositionLocalProvider(LocalAppBarTopPadding provides barTopPadding) {
                LibraryTabScreen(
                    state = state,
                    nowPlaying = nowPlaying,
                    reselectTick = 0,
                    overscroll = overscroll,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barTopPadding)
                    .topEdgeBlur(hazeState, glassHazeStyle(), background),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .height(GLASS_BAR_HEIGHT)
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
                val textStyle = textViewStyle(FIELD_TEXT_SIZE, 400, MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .focusRequester(focusRequester),
                    textStyle = textStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.search),
                                    style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                )
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    LibraryIconButton(
                        icon = Icons.Outlined.Close,
                        iconSize = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { query = "" },
                    )
                }
            }
        }
    }
}
