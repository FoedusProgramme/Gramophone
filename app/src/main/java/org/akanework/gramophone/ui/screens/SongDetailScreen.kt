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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBitrate
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.toLocaleString
import org.akanework.gramophone.logic.toMediaStoreId
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.components.home.LibraryCover
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceLabels
import org.akanework.gramophone.ui.components.settings.PreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.koin.compose.koinInject
import uk.akane.libphonograph.reader.FlowReader

/* One song's tags and file facts, as a page of rows under its cover. */

private val COVER_SIZE = 200.dp
private val COVER_CORNER = 28.dp

@Composable
fun SongDetailScreen(mediaId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reader = koinInject<FlowReader>()
    var looked by remember { mutableStateOf(false) }
    val item by produceState<MediaItem?>(null, mediaId) {
        val id = mediaId.toMediaStoreId()
        value = if (id == null) null else reader.idMapFlow.map { it[id] }.first()
        looked = true
    }
    // Gone from the library while its page was open: nothing to show.
    LaunchedEffect(looked, item) { if (looked && item == null) onBack() }
    val bitrate by produceState<Int?>(null, item) {
        val current = item ?: return@produceState
        value = withContext(Dispatchers.IO) { current.getBitrate(context.applicationContext) }
    }

    PreferenceScreen(title = stringResource(R.string.details), onBack = onBack, modifier = modifier) {
        val current = item ?: return@PreferenceScreen
        val metadata = current.mediaMetadata

        PreferenceSectionHeader(stringResource(R.string.album_cover))
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            LibraryCover(
                uri = metadata.artworkUri,
                defaultCover = R.drawable.ic_default_cover,
                cornerRadius = COVER_CORNER,
                modifier = Modifier.size(COVER_SIZE),
            )
        }
        Spacer(Modifier.height(8.dp))

        val tags = buildList {
            add(stringResource(R.string.dialog_title) to (metadata.title?.toString() ?: stringResource(R.string.unknown_title)))
            add(stringResource(R.string.dialog_artist) to (metadata.artist?.toString() ?: stringResource(R.string.unknown_artist)))
            add(stringResource(R.string.dialog_album) to (metadata.albumTitle?.toString() ?: stringResource(R.string.unknown_album)))
            add(stringResource(R.string.dialog_album_artist) to (metadata.albumArtist?.toString() ?: stringResource(R.string.unknown_artist)))
            add(stringResource(R.string.dialog_disc_number) to (metadata.discNumber?.toLocaleString() ?: ""))
            add(stringResource(R.string.dialog_track_number) to (metadata.trackNumber?.toLocaleString() ?: ""))
            if (hasImprovedMediaStore()) {
                add(stringResource(R.string.dialog_genre) to (metadata.genre?.toString() ?: stringResource(R.string.unknown_genre)))
            }
            add(
                stringResource(R.string.dialog_year) to
                    ((metadata.releaseYear ?: metadata.recordingYear)?.toLocaleString()
                        ?: stringResource(R.string.unknown_year))
            )
        }
        PreferenceGroup(tags) { (label, value), shape ->
            PreferenceRow(shape) { PreferenceLabels(label, subtitle = value) }
        }

        Spacer(Modifier.height(16.dp))
        val file = listOf(
            stringResource(R.string.dialog_duration) to
                (metadata.durationMs?.let { convertDurationToTimeStamp(it) } ?: ""),
            stringResource(R.string.dialog_bitrate) to when {
                bitrate != null -> stringResource(R.string.bitrate_format, bitrate!! / 1000)
                looked && item != null && bitrate == null -> stringResource(R.string.bitrate_unknown)
                else -> ""
            },
            stringResource(R.string.dialog_mime) to (current.localConfiguration?.mimeType ?: "(null)"),
            stringResource(R.string.dialog_path) to
                (current.getFile()?.path ?: current.requestMetadata.mediaUri?.toString() ?: "(null)"),
        )
        PreferenceGroup(file) { (label, value), shape ->
            PreferenceRow(shape) { PreferenceLabels(label, subtitle = value) }
        }
    }
}
