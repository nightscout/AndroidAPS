package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import java.nio.ByteBuffer
import java.security.MessageDigest

class CmdUnPair(
    name: String, val password: String,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseCmd(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var sn: String?
    var randomPassword: ByteArray? = null

    init {
        port = "0E0E"
        sn = name.replace("Equil - ", "").trim { it <= ' ' }
        sn = convertString(sn!!)
    }

    fun clear1(): EquilResponse? {
        try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(Utils.hexStringToBytes(sn!!))
            val pwd = messageDigest.digest()

            val equilPassword = AESUtil.getEquilPassWord(password)
            randomPassword = Utils.generateRandomPassword(32)
            val data = Utils.concat(equilPassword, randomPassword!!)
            val equilCmdModel = AESUtil.aesEncrypt(pwd, data)
            return responseCmd(equilCmdModel, "0D0D0000")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun getEquilResponse(): EquilResponse? {
        response = EquilResponse(createTime)
        return clear1()
    }

    override fun getNextEquilResponse(): EquilResponse? {
        return getEquilResponse()
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
                return list
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
        val equilCmdModel = decodeModel()
        val keyBytes = randomPassword!!

        val data2 = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val content = AESUtil.decrypt(equilCmdModel, keyBytes)
        val pwd1 = content.substring(0, 64)
        val pwd2 = content.substring(64)
        runPwd = pwd2
        val data1 = Utils.hexStringToBytes(pwd1)
        val data = Utils.concat(data1, data2)

        val equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd!!), data)
        runCode = equilCmdModel.code
        return responseCmd(equilCmdModel2, port + runCode)
    }

    override fun decodeConfirm(): EquilResponse? {
        //val equilCmdModel = decodeModel()
        //val content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd))
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
        return null
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = EquilHistoryRecord.EventType.UNPAIR_EQUIL
}
