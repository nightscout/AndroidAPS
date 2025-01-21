package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.LongNonPreferenceKey

enum class RileyLinkLongKey(
    override val key: String,
    override val defaultValue: Long
) : LongNonPreferenceKey {

    LastGoodDeviceCommunicationTime("AAPS.RileyLink.lastGoodDeviceCommunicationTime", 0L),
}