package app.aaps.pump.equil.manager.command

import android.util.Log
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import java.nio.ByteBuffer
import java.security.MessageDigest

class CmdPair(
    name: String,
    var address: String,
    private var password: String,
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
        Log.e(LTag.PUMPCOMM.toString(), "sn===$sn")
    }

    override fun getEquilResponse(): EquilResponse? {
        response = EquilResponse(createTime)
        try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(Utils.hexStringToBytes(sn!!))
            val pwd = messageDigest.digest()

            //B0EB6308060F79D685D6269DC048E32E4C103CD2B8EEA2DE4637EB8A5D6BCD08
            val equilPassword = AESUtil.getEquilPassWord(password)


            randomPassword = Utils.generateRandomPassword(32)
            val data = Utils.concat(equilPassword, randomPassword!!)
            aapsLogger.debug(LTag.PUMPCOMM, "pwd==" + Utils.bytesToHex(pwd))
            aapsLogger.debug(LTag.PUMPCOMM, "data==" + Utils.bytesToHex(data))
            val equilCmdModel = AESUtil.aesEncrypt(pwd, data)
            return responseCmd(equilCmdModel, "0D0D0000")
        } catch (e: Exception) {
            e.printStackTrace()
        }


        return null
    }

    override fun getNextEquilResponse(): EquilResponse? = getEquilResponse()

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
            response = EquilResponse(createTime)
            aapsLogger.debug(LTag.PUMPCOMM, "decode error=====" + e.message)
        }
        return null
    }

    override fun decode(): EquilResponse? {
        val equilCmdModel = decodeModel()
        val keyBytes = randomPassword ?: return null
        val content = AESUtil.decrypt(equilCmdModel, keyBytes)
        val pwd1 = content.substring(0, 64)
        val pwd2 = content.substring(64)
        aapsLogger.debug(LTag.PUMPCOMM, "decrypted====$pwd1")
        aapsLogger.debug(LTag.PUMPCOMM, "decrypted====$pwd2")
        if (ERROR_PWD == pwd1 && ERROR_PWD == pwd2) {
            synchronized(this) {
                cmdSuccess = true
                enacted = false
                (this as Object).notifyAll()
            }
            return null
        }

        preferences.put(EquilStringKey.Password, pwd2)
        preferences.put(EquilStringKey.Device, pwd1)
        runPwd = pwd2
        val data1 = Utils.hexStringToBytes(pwd1)
        val data = Utils.concat(data1, keyBytes)
        val equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd!!), data)
        runCode = equilCmdModel.code
        return responseCmd(equilCmdModel2, port + runCode)
    }

    override fun decodeConfirm(): EquilResponse? {
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notifyAll()
        }
        return null
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = EquilHistoryRecord.EventType.INITIALIZE_EQUIL

    companion object {

        const val ERROR_PWD: String = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
