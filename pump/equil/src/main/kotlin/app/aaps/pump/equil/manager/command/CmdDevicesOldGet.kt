package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Locale

class CmdDevicesOldGet(
    var address: String,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    private var firmwareVersion = 0f

    init {
        port = "0E0E"
    }

    override fun getEquilResponse(): EquilResponse {
        config = false
        isEnd = false
        response = EquilResponse(createTime)
        val temp = EquilResponse(createTime)
        val buffer = ByteBuffer.allocate(14)
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x0E.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x80.toByte())
        buffer.put(0x78.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x01.toByte())
        buffer.put(0x7B.toByte())
        buffer.put(0x02.toByte())
        buffer.put(0x00.toByte())
        temp.add(buffer)
        return temp
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x00)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x00, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeEquilPacket(data: ByteArray): EquilResponse? {
        if (!checkData(data)) {
            return null
        }
        val code = data[4]
        val intValue = getIndex(code)
        if (config) {
            if (rspIndex == intValue) {
                return null
            }
            val flag = isEnd(code)
            val buffer = ByteBuffer.wrap(data)
            response!!.add(buffer)
            if (!flag) {
                return null
            }
            try {
                val list = decodeConfirm()
                isEnd = true
                response = EquilResponse(createTime)
                rspIndex = intValue
                aapsLogger.debug(LTag.PUMPCOMM, "intValue=====$intValue====$rspIndex")
                return list
            } catch (e: Exception) {
                response = EquilResponse(createTime)
                aapsLogger.debug(LTag.PUMPCOMM, "decodeConfirm error =====" + e.message)
            }

            return null
        }
        val flag = isEnd(code)
        val buffer = ByteBuffer.wrap(data)
        response!!.add(buffer)
        if (!flag) {
            return null
        }
        try {
            val list = decode()
            response = EquilResponse(createTime)
            config = true
            rspIndex = intValue
            aapsLogger.debug(LTag.PUMPCOMM, "intValue=====$intValue====$rspIndex")
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            response = EquilResponse(createTime)
            aapsLogger.debug(LTag.PUMPCOMM, "decode error=====" + e.message)
        }
        return null
    }

    override fun decode(): EquilResponse? {
        val reqModel = decodeModel()
        val data = Utils.hexStringToBytes(reqModel.ciphertext!!)
        val fv = data[12].toString() + "." + data[13]
        firmwareVersion = fv.toFloat()
        aapsLogger.debug(
            LTag.PUMPCOMM, "CmdDevicesOldGet====" +
                Utils.bytesToHex(data) + "========" + firmwareVersion + "===" + (firmwareVersion < EquilConst.EQUIL_SUPPORT_LEVEL)
        )
        reqModel.ciphertext = Utils.bytesToHex(getNextData())
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
        return responseCmd(reqModel, "0000" + reqModel.code)
    }

    override fun decodeModel(): EquilCmdModel {
        val equilCmdModel = EquilCmdModel()
        val list: MutableList<Byte?> = ArrayList<Byte?>()
        var index = 0
        for (b in response!!.send) {
            if (index == 0) {
                val bs = b.array()
                val codeByte = byteArrayOf(bs[10], bs[11])
                list.add(bs[bs.size - 2])
                list.add(bs[bs.size - 1])
                equilCmdModel.code = Utils.bytesToHex(codeByte)
            } else {
                val bs = b.array()
                for (i in 6 until bs.size) {
                    list.add(bs[i])
                }
            }
            index++
        }
        val list3 = list.subList(0, list.size)
        equilCmdModel.ciphertext = Utils.bytesToHex(list3).lowercase(Locale.getDefault())
        equilCmdModel.tag = ""
        equilCmdModel.iv = ""
        return equilCmdModel
    }

    override fun decodeConfirmData(data: ByteArray) {
        val value = Utils.bytesToInt(data[7], data[6])
        val fv = data[18].toString() + "." + data[19]
        firmwareVersion = fv.toFloat()
        aapsLogger.debug(
            LTag.PUMPCOMM, ("CmdDevicesOldGet====" +
                Utils.bytesToHex(data) + "=====" + value + "===" + firmwareVersion + "===="
                + (firmwareVersion < EquilConst.EQUIL_SUPPORT_LEVEL))
        )
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
    }

    fun isSupport(): Boolean = firmwareVersion >= EquilConst.EQUIL_SUPPORT_LEVEL

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}
