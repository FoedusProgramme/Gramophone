/*
 *     Copyright (C) 2026 nift4
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

package org.akanework.gramophone.logic.utils

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo

class MediaItemList(val list: List<MediaItem>) : Binder() {
    private val blr by lazy {
        BundleListRetriever(list.map {
            it.toBundleIncludeLocalConfiguration(MediaLibraryInfo.INTERFACE_VERSION)
        })
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == FIRST_CALL_TRANSACTION) {
            return blr.transact(code, data, reply, flags)
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        fun getList(binder: IBinder): List<MediaItem> {
            if (binder is MediaItemList) {
                return binder.list
            }
            return BundleListRetriever.getList(binder)
                .map { MediaItem.fromBundle(it, MediaLibraryInfo.INTERFACE_VERSION) }
        }
    }
}
