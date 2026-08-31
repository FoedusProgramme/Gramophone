/*
 *     Copyright (C) 2025 The Gramophone authors
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

package uk.akane.libphonograph.items

import androidx.media3.common.MediaItem
import kotlin.math.min

interface FileNode {
    val folderName: String
    val folderList: Map<String, FileNode>
    val songList: List<MediaItem>
    val albumId: Long?
    val addDate: Long?
        get() = min(songList.minOfOrNull { it.mediaMetadata.addDate ?: Long.MAX_VALUE }
            ?: Long.MAX_VALUE,
            folderList.minOfOrNull { it.value.addDate ?: Long.MAX_VALUE } ?: Long.MAX_VALUE).let {
            if (it == Long.MAX_VALUE) null else it
        }
    val modifiedDate: Long?
        get() = maxOf(songList.maxOfOrNull { it.mediaMetadata.modifiedDate ?: Long.MIN_VALUE }
            ?: Long.MIN_VALUE,
            folderList.maxOfOrNull { it.value.modifiedDate ?: Long.MIN_VALUE }
                ?: Long.MIN_VALUE).let {
            if (it == Long.MIN_VALUE) null else it
        }
}

object EmptyFileNode : FileNode {
    override val folderName: String
        get() = ""
    override val folderList = mapOf<String, FileNode>()
    override val songList = listOf<MediaItem>()
    override val albumId = null
}