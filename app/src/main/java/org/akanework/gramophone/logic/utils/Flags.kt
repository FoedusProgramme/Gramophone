package org.akanework.gramophone.logic.utils

object Flags {
    const val TEST_RG_OFFLOAD = false // test only
    const val TTML_AGENT_SMART_SIDES = true

    // Before turning it on in prod we need i18n.
    const val FORMAT_INFO_DIALOG = true // TODO(ASAP)

    // Before turning offload to true in prod we'd need a conflict resolution UI in case DPE is not
    // offloadable and RG is turned on while user tries to turn on offload (and other way around).
    const val OFFLOAD = false

    // It uses MediaStore favorites and I'm not sure if that was a good idea
    const val FAVORITE_SONGS = false // TODO(ASAP)
    var PLAYLIST_EDITING: Boolean? = null // TODO(ASAP)
}
