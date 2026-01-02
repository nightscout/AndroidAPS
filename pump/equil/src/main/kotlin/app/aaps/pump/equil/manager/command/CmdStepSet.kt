package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils

class CmdStepSet(
    var sendConfig: Boolean, var step: Int,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x07)
        val data3 = Utils.intToBytes(step)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x07, 0x01)
        val data3 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
//        byte[] byteData = Crc.hexStringToBytes(data);
//        int status = byteData[6] & 0xff;
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun decodeConfirm(): EquilResponse? {
        val equilCmdModel = decodeModel()
        runCode = equilCmdModel.code
        val content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd!!))
        decodeConfirmData(Utils.hexStringToBytes(content))
        val data: ByteArray? = getNextData()
        val equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd!!), data)
        if (sendConfig) {
            return responseCmd(equilCmdModel2, port + runCode)
        }
        return null
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}
