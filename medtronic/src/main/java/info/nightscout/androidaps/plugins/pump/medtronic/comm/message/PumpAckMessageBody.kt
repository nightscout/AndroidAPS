package info.nightscout.androidaps.plugins.pump.medtronic.comm.message

/**
 * Created by geoff on 5/29/16.
 */
class PumpAckMessageBody : CarelinkShortMessageBody {

    constructor() {
        init(byteArrayOf(0))
    }

    constructor(bodyData: ByteArray?) {
        init(bodyData)
    }
}