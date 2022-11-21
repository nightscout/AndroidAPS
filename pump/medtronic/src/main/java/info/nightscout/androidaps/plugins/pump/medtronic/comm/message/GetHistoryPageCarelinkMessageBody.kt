package info.nightscout.androidaps.plugins.pump.medtronic.comm.message

import info.nightscout.pump.core.utils.ByteUtil
import kotlin.experimental.and

/**
 * Created by geoff on 6/2/16.
 */
class GetHistoryPageCarelinkMessageBody : CarelinkLongMessageBody {

    // public boolean wasLastFrame = false;
    // public int frameNumber = 0;
    // public byte[] frame = new byte[] {};
    constructor(frameData: ByteArray?) {
        init(frameData)
    }

    constructor(pageNum: Int) {
        init(pageNum)
    }

    override val length: Int
        get() = data!!.size

    fun init(pageNum: Int) {
        val numArgs: Byte = 1
        super.init(byteArrayOf(numArgs, pageNum.toByte()))
    }

    val frameNumber: Int
        get() = if (data!!.size > 0) {
            (data!![0] and 0x7f.toByte()).toInt()
        } else 255

    fun wasLastFrame(): Boolean {
        return (data!![0] and 0x80.toByte()).toInt() != 0
    }

    val frameData: ByteArray
        get() {
            return if (data == null) {
                byteArrayOf()
            } else {
                ByteUtil.substring(data!!, 1, data!!.size - 1)
            }
        }
}