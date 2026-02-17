package org.akanework.gramophone.logic.utils

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.util.LinkedList

object PlayerListHelp {
    fun dumpPlaylist(instance: Player): Pair<MutableList<Int>, MutableList<MediaItem>> {
        val items = LinkedList<MediaItem>()
        for (i in 0 until instance.mediaItemCount) {
            items.add(instance.getMediaItemAt(i))
        }
        val indexes = LinkedList<Int>()
        val s = instance.shuffleModeEnabled
        var i = instance.currentTimeline.getFirstWindowIndex(s)
        while (i != C.INDEX_UNSET) {
            indexes.add(i)
            i = instance.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
        }
        return Pair(indexes, items)
    }
}