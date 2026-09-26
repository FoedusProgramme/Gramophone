/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone.logic.init

import androidx.media3.common.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.map.AndroidUriMapper
import coil3.request.NullRequestDataException
import coil3.util.Logger
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.CoilArtPipeline
import java.io.IOException

/** Builds the app-wide Coil [ImageLoader] wired to [CoilArtPipeline]. */
object ImageLoaderFactory {

    fun create(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                add(CoilArtPipeline.ThumbnailKeyer())
                add(CoilArtPipeline.AlbumThumbnailKeyer())
                add(CoilArtPipeline.AudioCoverKeyer())
                add(AndroidUriMapper())
                add(CoilArtPipeline.ThumbnailMapper())
                add(CoilArtPipeline.AudioCoverMapper())
                add(CoilArtPipeline.AlbumThumbnailMapper())
                add(CoilArtPipeline.ThumbnailFetcherFactory())
                add(CoilArtPipeline.AlbumThumbnailFetcherFactory())
                add(CoilArtPipeline.SongCoverFetcherFactory())
            }
            .run {
                if (!BuildConfig.DEBUG) this else
                    logger(object : Logger {
                        override var minLevel = Logger.Level.Verbose
                        override fun log(
                            tag: String,
                            level: Logger.Level,
                            message: String?,
                            throwable: Throwable?
                        ) {
                            if (level < minLevel) return
                            val println = { it: String ->
                                when (level) {
                                    Logger.Level.Verbose -> Log.d(tag, it)
                                    Logger.Level.Debug -> Log.d(tag, it)
                                    Logger.Level.Info -> Log.i(tag, it)
                                    Logger.Level.Warn -> Log.w(tag, it)
                                    Logger.Level.Error -> Log.e(tag, it)
                                }
                            }
                            if (message != null) {
                                println(message)
                            }
                            // Let's keep the log readable and ignore normal events' stack traces.
                            if (throwable != null && throwable !is NullRequestDataException
                                && throwable !is CoilArtPipeline.NoAlbumArtException
                                && (throwable !is IOException
                                        || throwable.message != "No album art found"
                                        && throwable.message != "No embedded album art found"
                                        && throwable.message != "No thumbnails in Downloads directories"
                                        && throwable.message != "No thumbnails in top-level directories")
                            ) {
                                println(Log.getThrowableString(throwable)!!)
                            }
                        }
                    })
            }
            .build()
    }
}
