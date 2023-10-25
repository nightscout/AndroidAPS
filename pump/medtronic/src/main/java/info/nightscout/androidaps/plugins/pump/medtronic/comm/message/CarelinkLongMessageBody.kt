package info.nightscout.androidaps.plugins.pump.medtronic.comm.message

/**
 * Created by geoff on 6/2/16.
 */
open class CarelinkLongMessageBody : MessageBody {

    //protected var data: ByteArray? = null

    internal constructor() {
        init(ByteArray(0))
    }

    constructor(payload: ByteArray) {
        init(payload)
    }

    override fun init(rxData: ByteArray?) {
        if (rxData != null && rxData.size == LONG_MESSAGE_BODY_LENGTH) {
            data = rxData
        } else {
            data = ByteArray(LONG_MESSAGE_BODY_LENGTH)
            if (rxData != null) {
                val size = if (rxData.size < LONG_MESSAGE_BODY_LENGTH) rxData.size else LONG_MESSAGE_BODY_LENGTH
                for (i in 0 until size) {
                    data!![i] = rxData[i]
                }
            }
        }
    }

    override val length: Int
        get() = LONG_MESSAGE_BODY_LENGTH

    // {
    //     return LONG_MESSAGE_BODY_LENGTH
    // }

    // override fun getTxData(): ByteArray {
    //     return data
    // }

    companion object {
        private const val LONG_MESSAGE_BODY_LENGTH = 65
    }
}