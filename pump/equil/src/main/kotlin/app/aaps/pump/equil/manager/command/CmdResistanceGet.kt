package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils

class CmdResistanceGet(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    init {
        port = "1515"
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x02)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x02, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        val value = Utils.bytesToInt(data[7], data[6])
        cmdStatus = true
        enacted = value >= 500
        synchronized(this) {
            (this as Object).notify()
        }
    }

    override fun decodeConfirm(): EquilResponse? {
        val equilCmdModel = decodeModel()
        runCode = equilCmdModel.code
        val content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd!!))
        decodeConfirmData(Utils.hexStringToBytes(content))
        //val data: ByteArray? = getNextData()
        //val equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data)
        //        return responseCmd(equilCmdModel2, port + runCode,true);
        return null
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}
