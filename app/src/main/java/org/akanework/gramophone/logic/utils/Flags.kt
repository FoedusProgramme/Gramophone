package org.akanework.gramophone.logic.utils

object Flags {
    const val TEST_RG_OFFLOAD = false // test only
    const val TTML_AGENT_SMART_SIDES = true
    const val HIDE_SAME_TRANSLATIONS = true
    const val IGNORE_SMALL_ENDTIME_GAPS = true

    // Before turning it on in prod we need i18n.
    const val FORMAT_INFO_DIALOG = true // TODO(ASAP)

    // Before turning offload to true in prod we'd need a conflict resolution UI in case DPE is not
    // offloadable and RG is turned on while user tries to turn on offload (and other way around).
    const val OFFLOAD = false

    // Multiple queues
    const val MQ_PREVIEW = false
}
