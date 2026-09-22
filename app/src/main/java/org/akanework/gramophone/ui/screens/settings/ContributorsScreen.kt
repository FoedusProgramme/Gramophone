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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.Contributors
import org.akanework.gramophone.logic.utils.data.GitHubUser
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceScreen

private const val WEBLATE_URL = "https://hosted.weblate.org/engage/gramophone/"
private val AVATAR_SIZE = 36.dp

/** Everyone in [Contributors.LIST], then the translators as one row, each linking to its page. */
@Composable
fun ContributorsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val translators = remember { Contributors.TRANSLATORS.joinToString() }
    PreferenceScreen(title = stringResource(R.string.settings_contributors), onBack = onBack, modifier = modifier) {
        PreferenceGroup(Contributors.LIST.size + 1) { index, shape ->
            if (index < Contributors.LIST.size) {
                ContributorRow(shape, Contributors.LIST[index])
            } else {
                PersonRow(
                    shape,
                    url = WEBLATE_URL,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(AVATAR_SIZE).padding(6.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    name = stringResource(R.string.translators),
                    login = null,
                    subtitle = translators,
                )
            }
        }
    }
}

@Composable
private fun ContributorRow(shape: Shape, contributor: GitHubUser) {
    PersonRow(
        shape,
        url = if (contributor.link) "https://github.com/${contributor.login}" else null,
        icon = {
            AsyncImage(
                model = contributor.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape),
            )
        },
        name = contributor.name,
        login = contributor.login,
        subtitle = stringResource(contributor.contributed),
    )
}

@Composable
private fun PersonRow(
    shape: Shape,
    url: String?,
    icon: @Composable () -> Unit,
    name: String?,
    login: String?,
    subtitle: String,
) {
    val context = LocalContext.current
    PreferenceRow(
        shape,
        onClick = if (url == null) null else {
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_LONG).show()
                }
            }
        },
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name ?: login ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (name != null && name != login && login != null) {
                    Text(
                        text = "@$login",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
