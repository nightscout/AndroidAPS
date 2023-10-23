package info.nightscout.androidaps.plugins.pump.medtronic.comm.message

/**
 * Created by geoff on 5/29/16.
 */
class UnknownMessageBody(override var txData: ByteArray) : MessageBody() {

    override val length: Int
        get() = 0

    override fun init(rxData: ByteArray?) {
        data = rxData
    }

}