package app.aaps.pump.common.hw.rileylink.ble.data.encoding

import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException

/**
 * Created by andy on 11/24/18.
 */
interface Encoding4b6b {

    fun encode4b6b(data: ByteArray): ByteArray

    @Throws(RileyLinkCommunicationException::class)
    fun decode4b6b(raw: ByteArray): ByteArray
}
