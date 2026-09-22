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

package org.akanework.gramophone.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LibraryIconButton

/**
 * The library list from AboutLibraries under the glass bar. The list is its own lazy column,
 * so it takes the bar's height as content padding and the bar keeps its title all the time.
 */
@Composable
fun OssLicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val hazeState = remember { HazeState() }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    // The list paints the theme's background itself, so the page takes the same colour and the
    // bar frosts one continuous surface instead of showing a step where the list starts.
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState).background(background),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                top = insets.calculateTopPadding() + GLASS_BAR_HEIGHT,
                end = insets.calculateEndPadding(layoutDirection),
                bottom = insets.calculateBottomPadding(),
            ),
        )
        GlassTitleBar(
            hazeState = hazeState,
            title = stringResource(R.string.settings_open_source_licenses),
            scrolled = { Float.MAX_VALUE },
            toolbarPaddingStart = 10.dp,
            titlePaddingStart = 4.dp,
            navigationIcon = {
                LibraryIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            },
        )
    }
}
