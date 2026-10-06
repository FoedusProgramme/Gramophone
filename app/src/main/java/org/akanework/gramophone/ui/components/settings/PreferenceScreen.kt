/*
 *     Copyright (C) 2026 Akane Foundation
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

package org.akanework.gramophone.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LargeTitle
import org.akanework.gramophone.ui.components.home.PAGE_TITLE_TOP_GAP
import org.akanework.gramophone.ui.components.home.PageTitleStyle
import org.akanework.gramophone.ui.components.home.rememberLargeTitleState
import org.akanework.gramophone.ui.theme.LocalCardSurface

/*
 * A page of grouped settings,
 * each group is one rounded block of rows, with the block's own big corners at its two ends and
 * small ones where the rows meet, under the same glass bar and large title as the rest of the
 * app.
 */

private val GROUP_MARGIN = 16.dp
private val GROUP_CORNER = 24.dp
private val ROW_CORNER = 4.dp
private val ROW_PADDING = 16.dp

/** Material's disabled content alpha. */
const val PREFERENCE_DISABLED_ALPHA = 0.38f

/**
 * A page of grouped settings: the large title the bar takes over as it scrolls under, and the
 * same frosted bar as the rest of the app. [content] emits into the scrolling
 * column, usually [PreferenceSectionHeader]s and [PreferenceGroup]s.
 */
@Composable
fun PreferenceScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val titleState = rememberLargeTitleState()
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    // How far the content has moved from rest.
    val scrolled = { scrollState.value.toFloat() }
    // The same ground as the home, with the same cards on it.
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier
            .fillMaxSize()
            .background(background)
    ) {
        // The content sits behind the frosted bar as its blur source, padded clear of it at the
        // top. The background is painted inside the source so the recorded layer is opaque.
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(background)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = insets.calculateStartPadding(layoutDirection),
                        top = insets.calculateTopPadding() + GLASS_BAR_HEIGHT,
                        end = insets.calculateEndPadding(layoutDirection),
                        bottom = insets.calculateBottomPadding() + 24.dp,
                    )
            ) {
                LargeTitle(
                    title, titleState, scrolled,
                    maxLines = 2,
                    style = PageTitleStyle,
                    topGap = PAGE_TITLE_TOP_GAP,
                    bottomGap = 4.dp,
                )
                content()
            }
        }
        GlassTitleBar(
            hazeState = hazeState,
            title = title,
            scrolled = scrolled,
            onBack = onBack,
            titleTopGap = PAGE_TITLE_TOP_GAP,
            actions = actions,
        )
    }
}

/**
 * What a section header keeps above itself. A page whose first group has no header puts the
 * same gap before it, so its first row starts where a header's text would.
 */
val SECTION_HEADER_TOP_GAP = 20.dp

@Composable
fun PreferenceSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = GROUP_MARGIN + ROW_PADDING,
            end = GROUP_MARGIN,
            top = SECTION_HEADER_TOP_GAP,
            bottom = 8.dp,
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** The corners of row [index] of [count]: the group's own at its two ends, small ones between. */
fun preferenceRowShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) GROUP_CORNER else ROW_CORNER,
    topEnd = if (index == 0) GROUP_CORNER else ROW_CORNER,
    bottomEnd = if (index == count - 1) GROUP_CORNER else ROW_CORNER,
    bottomStart = if (index == count - 1) GROUP_CORNER else ROW_CORNER,
)

/**
 * The rows of one group, as a single rounded block: the block's own corners at its two ends and
 * small ones where the rows meet, separated by a hairline gap.
 */
@Composable
fun PreferenceGroup(count: Int, row: @Composable (index: Int, shape: Shape) -> Unit) {
    if (count <= 0) return
    Column(
        modifier = Modifier.padding(horizontal = GROUP_MARGIN),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { index -> row(index, preferenceRowShape(index, count)) }
    }
}

/** One row per item of [items]. */
@Composable
fun <T> PreferenceGroup(items: List<T>, row: @Composable (item: T, shape: Shape) -> Unit) {
    PreferenceGroup(items.size) { index, shape -> row(items[index], shape) }
}

/** The rows themselves, each given its shape. Conditional rows are left out by the caller. */
@Composable
fun PreferenceGroup(rows: List<@Composable (shape: Shape) -> Unit>) {
    PreferenceGroup(rows.size) { index, shape -> rows[index](shape) }
}

@Composable
fun PreferenceGroup(vararg rows: @Composable (shape: Shape) -> Unit) {
    PreferenceGroup(rows.asList())
}

@Composable
fun PreferenceRow(
    shape: Shape,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LocalCardSurface.current)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(ROW_PADDING)
            .alpha(if (enabled) 1f else PREFERENCE_DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A row's title: bodyLarge at semibold weight. */
val PreferenceTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

/** A row's text: what it is, and underneath, what it says. */
@Composable
fun PreferenceLabels(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(
            text = title,
            style = PreferenceTitleStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The mark at the start of a top-level row, followed by its gap to the labels: the icon on a
 * tinted disc.
 */
@Composable
fun PreferenceIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.width(16.dp))
}
