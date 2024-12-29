package app.aaps.pump.medtronic.comm.message

import app.aaps.core.utils.pump.ByteUtil

/**
 * Created by geoff on 5/29/16.
 */
open class MessageBody {

    protected var data: ByteArray? = null

    open val length: Int
        get() = 0

    open fun init(rxData: ByteArray?) {}

    open val txData: ByteArray?
        get() = if (data == null) byteArrayOf() else data

    override fun toString(): String {
        val sb = StringBuilder(javaClass.simpleName)
        sb.append(" [txData=")
        sb.append(ByteUtil.shortHexString(txData))
        sb.append("]")
        return sb.toString()
    }
}