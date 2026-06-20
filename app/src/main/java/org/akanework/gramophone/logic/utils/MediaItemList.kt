package org.akanework.gramophone.logic.utils

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem

class MediaItemList(val list: List<MediaItem>) : Binder() {
    private val blr by lazy { BundleListRetriever(list.map { it.toBundleIncludeLocalConfiguration() }) }
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
            return BundleListRetriever.getList(binder).map { MediaItem.fromBundle(it) }
        }
    }
}