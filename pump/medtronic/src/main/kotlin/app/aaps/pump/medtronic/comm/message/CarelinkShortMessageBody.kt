package app.aaps.pump.medtronic.comm.message

/**
 * Created by geoff on 5/29/16.
 */
// Andy: See comments in message body
open class CarelinkShortMessageBody : MessageBody {

    //var body: ByteArray?

    constructor() {
        init(byteArrayOf(0))
    }

    constructor(data: ByteArray?) {
        init(data)
    }

    override val length: Int
        get() = data!!.size

    override fun init(rxData: ByteArray?) {
        data = rxData
    }

    var rxData: ByteArray?
        get() = data
        set(rxData) {
            init(rxData)
        }

    // override var txData: ByteArray?
    //     get() = body
    //     set(txData) {
    //         init(txData)
    //     }

}