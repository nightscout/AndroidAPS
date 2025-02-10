package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class RileyLinkLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastGoodDeviceCommunicationTime("AAPS.RileyLink.lastGoodDeviceCommunicationTime", 0L),
}