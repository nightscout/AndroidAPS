package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import java.lang.Exception
import java.nio.ByteBuffer

abstract class BaseSetting(
    createTime: Long,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseCmd(createTime, aapsLogger, preferences, equilManager) {

    fun getReqData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val tzm = Utils.hexStringToBytes(getEquilDevices())
        val data = Utils.concat(indexByte, tzm)
        pumpReqIndex++
        return data
    }

    override fun getEquilResponse(): EquilResponse? {
        config = false
        isEnd = false
        response = EquilResponse(createTime)
        val pwd = Utils.hexStringToBytes(getEquilPassWord())
        val data = getReqData()
        var equilCmdModel: EquilCmdModel?
        try {
            equilCmdModel = AESUtil.aesEncrypt(pwd, data)
            return responseCmd(equilCmdModel, DEFAULT_PORT + "0000")
        } catch (_: Exception) {
            synchronized(this) {
                cmdStatus = false
            }
        }
        return null
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
                val response1 = decodeConfirm()
                isEnd = true
                response = EquilResponse(createTime)
                rspIndex = intValue
                return response1
            } catch (e: Exception) {
                response = EquilResponse(createTime)
                aapsLogger.debug(LTag.PUMPCOMM, "decodeEquilPacket error =====" + e.message)
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
            return list
        } catch (e: Exception) {
            response = EquilResponse(createTime)
            aapsLogger.debug(LTag.PUMPCOMM, "decodeEquilPacket error=====" + e.message)
        }
        return null
    }

    override fun decode(): EquilResponse? {
        val reqModel = decodeModel()
        val pwd = Utils.hexStringToBytes(getEquilPassWord())
        val content = AESUtil.decrypt(reqModel, pwd)
        val pwd2 = content.substring(8)
        runPwd = pwd2
        val data = getFirstData()
        val equilCmdModel = AESUtil.aesEncrypt(Utils.hexStringToBytes(pwd2), data)
        runCode = reqModel.code
        return responseCmd(equilCmdModel, port + runCode)
    }

    override fun getNextEquilResponse(): EquilResponse? {
        aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse=== start11 ")
        config = true
        isEnd = false
        response = EquilResponse(createTime)
        val data = getFirstData()
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse=== start ")
            var equilCmdModel: EquilCmdModel = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd!!), data)
            return responseCmd(equilCmdModel, port + runCode)
        } catch (e: Exception) {
            aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse===" + e.message)
            synchronized(this) {
                cmdStatus = false
            }
        }
        return null
    }

    abstract fun getFirstData(): ByteArray?

    abstract fun getNextData(): ByteArray?

    abstract fun decodeConfirmData(data: ByteArray)

    override fun decodeConfirm(): EquilResponse? {
        val equilCmdModel = decodeModel()
        runCode = equilCmdModel.code
        val content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd!!))
        decodeConfirmData(Utils.hexStringToBytes(content))
        val data = getNextData()
        val equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd!!), data)
        return responseCmd(equilCmdModel2, port + runCode)
    }
}
