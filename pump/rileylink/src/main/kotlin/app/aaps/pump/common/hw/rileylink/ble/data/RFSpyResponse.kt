package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.defs.RFSpyRLResponse
import javax.inject.Inject
import javax.inject.Provider

/**
 * Created by geoff on 5/26/16.
 */
class RFSpyResponse @Inject constructor(
    private val radioResponseProvider: Provider<RadioResponse>
) {

    // 0xaa == timeout
    // 0xbb == interrupted
    // 0xcc == zero-data
    // 0xdd == success
    // 0x11 == invalidParam
    // 0x22 == unknownCommand
    private var radioResponse: RadioResponse? = null
    private var command: RileyLinkCommand? = null
    lateinit var raw: ByteArray

    fun with(command: RileyLinkCommand?, raw: ByteArray): RFSpyResponse {
        this.command = command
        this.raw = raw
        return this
    }

    fun getRadioResponse(): RadioResponse {
        if (looksLikeRadioPacket()) {
            radioResponse = radioResponseProvider.get().with(command)
            radioResponse?.init(raw)
        } else {
            radioResponse = radioResponseProvider.get()
        }
        return radioResponse ?: throw IllegalStateException()
    }

    fun wasNoResponseFromRileyLink(): Boolean = raw.isEmpty()

    fun wasTimeout(): Boolean {
        if ((raw.size == 1) || (raw.size == 2)) {
            return raw[0] == 0xaa.toByte()
        }
        return false
    }

    fun wasInterrupted(): Boolean {
        if ((raw.size == 1) || (raw.size == 2)) {
            return raw[0] == 0xbb.toByte()
        }
        return false
    }

    fun isInvalidParam(): Boolean {
        if ((raw.size == 1) || (raw.size == 2)) {
            return raw[0] == 0x11.toByte()
        }
        return false
    }

    fun isUnknownCommand(): Boolean {
        if ((raw.size == 1) || (raw.size == 2)) {
            return raw[0] == 0x22.toByte()
        }
        return false
    }

    fun isOK(): Boolean {
        if ((raw.size == 1) || (raw.size == 2)) {
            return raw[0] == 0x01.toByte() || raw[0] == 0xDD.toByte()
        }
        return false
    }

    fun looksLikeRadioPacket(): Boolean {
        return raw.size > 2
    }

    override fun toString(): String {
        if (raw.size > 2) {
            return "Radio packet"
        } else {
            val r = RFSpyRLResponse.fromByte(raw[0])
            return r?.toString() ?: ""
        }
    }
}
