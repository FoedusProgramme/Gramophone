package org.akanework.gramophone.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.flowOf
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter

class SongPickerActivity : PickerActivity<MediaItem>() {
    override fun makeAdapter() =
        SongAdapter(
            null,
            flowOf("MISSING TITLE: (SongPickerActivity)"),
            rawOrderExposed = Sorter.Type.ByTitleAscending,
            isSubFragment = R.id.songs,
            fallbackContext = this
        )

    override fun getTitleStr() = getString(R.string.picker_activity)

    fun onSelected(item: MediaItem) {
        setResult(RESULT_OK, Intent().apply {
            setDataAndType(ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                item.requireMediaStoreId()), item.localConfiguration?.mimeType)
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}