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

package uk.akane.libphonograph

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal inline fun <reified T, reified U> HashMap<T, U>.putIfAbsentSupport(key: T, value: U) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        putIfAbsent(key, value)
    } else {
        // Duh...
        if (!containsKey(key))
            put(key, value)
    }
}

abstract class ContentObserverCompat(handler: Handler?) : ContentObserver(handler) {
    final override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    final override fun onChange(selfChange: Boolean, uri: Uri?) {
        onChange(selfChange, uri, 0)
    }

    final override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
        if (uri == null)
            onChange(selfChange, emptyList(), flags)
        else
            onChange(selfChange, listOf(uri), flags)
    }

    abstract override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int)
    abstract override fun deliverSelfNotifications(): Boolean
}

internal fun versioningCallbackFlow(
    block: suspend ProducerScope<Long>.(() -> Long) -> Unit
): Flow<Long> {
    val versionTracker = AtomicLong()
    return callbackFlow { block(versionTracker::incrementAndGet) }
}

internal fun contentObserverVersioningFlow(
    context: Context, scope: CoroutineScope, uri: Uri,
    notifyForDescendants: Boolean
): Flow<Long> {
    return versioningCallbackFlow { nextVersion ->
        val listener = object : ContentObserverCompat(null) {
            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                // TODO can we use those uris and flags for incremental reload at least on newer
                //  platform versions? completely since R+, Q has no flags, before we get meh deletion handling
                scope.launch {
                    send(nextVersion())
                }
            }

            override fun deliverSelfNotifications(): Boolean {
                return true
            }
        }
        // Notifications may get delayed while we are frozen, but they do not get lost. Though, if
        // too many of them pile up, we will get killed for eating too much space with our async
        // binder transactions and we will have to restart in a new process later.
        context.contentResolver.registerContentObserver(uri, notifyForDescendants, listener)
        send(nextVersion())
        awaitClose {
            context.contentResolver.unregisterContentObserver(listener)
        }
    }
}

fun File.toUriCompat(): Uri {
    val tmp = Uri.fromFile(this)
    return if (tmp.scheme != "file") // weird os bug workaround, found on Samsung and Xiaomi
        tmp.buildUpon().scheme("file").build()
    else tmp
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Cursor.getStringOrNullIfThrow(index: Int): String? =
    try {
        getString(index)
    } catch (_: Exception) {
        null
    }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Cursor.getLongOrNullIfThrow(index: Int): Long? =
    try {
        getLong(index)
    } catch (_: Exception) {
        null
    }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Cursor.getIntOrNullIfThrow(index: Int): Int? =
    try {
        getInt(index)
    } catch (_: Exception) {
        null
    }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Cursor.getColumnIndexOrNull(columnName: String): Int? =
    getColumnIndex(columnName).let { if (it == -1) null else it }
