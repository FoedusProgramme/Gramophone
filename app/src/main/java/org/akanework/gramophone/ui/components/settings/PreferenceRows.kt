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

package org.akanework.gramophone.ui.components.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.akanework.gramophone.R
import kotlin.math.roundToInt

/* The kinds of row a settings page is made of. */

/** A switch. The whole row toggles it. */
@Composable
fun SwitchPreferenceRow(
    shape: Shape,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    PreferenceRow(shape, onClick = { onCheckedChange(!checked) }, enabled = enabled) {
        PreferenceLabels(title, Modifier.weight(1f), subtitle)
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A checkbox before its label, for the folder lists. */
@Composable
fun CheckboxPreferenceRow(
    shape: Shape,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceRow(shape, onClick = { onCheckedChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        PreferenceLabels(title, Modifier.weight(1f))
    }
}

/**
 * The title over a slider, the value beside it. [value] is only written back once the finger
 * lifts. The label follows the finger meanwhile.
 */
@Composable
fun SliderPreferenceRow(
    shape: Shape,
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    valueText: @Composable (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shown = dragging ?: value
    PreferenceRow(shape, enabled = enabled) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = PreferenceTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = shown.toFloat(),
                onValueChange = { dragging = it.roundToInt().coerceIn(range) },
                onValueChangeFinished = {
                    dragging?.let { if (it != value) onValueChange(it) }
                    dragging = null
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = enabled,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = valueText(shown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * The current entry as the subtitle, the others in a menu dropped from the row. [entries] and
 * [values] pair up by index.
 */
@Composable
fun DropdownPreferenceRow(
    shape: Shape,
    title: String,
    entries: List<String>,
    values: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = values.indexOf(value)
    Box(Modifier.fillMaxWidth()) {
        PreferenceRow(shape, onClick = { expanded = true }) {
            PreferenceLabels(title, Modifier.weight(1f), entries.getOrNull(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(entry) },
                    trailingIcon = if (index == selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        values.getOrNull(index)?.let { if (it != value) onValueChange(it) }
                    },
                )
            }
        }
    }
}

/** A note under the setting above it. */
@Composable
fun InfoPreferenceRow(shape: Shape, text: String) {
    PreferenceRow(shape) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens [url], or says so when nothing on the device can. */
@Composable
fun LinkPreferenceRow(shape: Shape, title: String, subtitle: String?, url: String) {
    val context = LocalContext.current
    PreferenceRow(
        shape,
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_LONG).show()
            }
        },
    ) {
        PreferenceLabels(title, Modifier.weight(1f), subtitle)
    }
}

/** A row that leads somewhere else: a sub page, a dialog. */
@Composable
fun NavigationPreferenceRow(
    shape: Shape,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    PreferenceRow(shape, onClick = onClick) {
        if (icon != null) PreferenceIcon(icon)
        PreferenceLabels(title, Modifier.weight(1f), subtitle)
    }
}
