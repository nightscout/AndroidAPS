package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilPacketCodec
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils

abstract class BaseCmd(
    val createTime: Long,
    val aapsLogger: AAPSLogger,
    val preferences: Preferences,
    val equilManager: EquilManager
) : CustomCommand {

    var resolvedResult: ResolvedResult = ResolvedResult.NONE
    var timeOut = 22000
    var connectTimeOut = 15000

    var port: String = "0404"
    var config: Boolean = false
    var isEnd: Boolean = false
    var cmdSuccess: Boolean = false
    var enacted = true
    var response: EquilResponse? = null
    var runPwd: String? = null
    var runCode: String? = null

    abstract fun getEquilResponse(): EquilResponse?
    abstract fun getNextEquilResponse(): EquilResponse?
    abstract fun decodeEquilPacket(data: ByteArray): EquilResponse?

    abstract fun decode(): EquilResponse?
    abstract fun decodeConfirm(): EquilResponse?

    abstract fun getEventType(): EquilHistoryRecord.EventType?

    override val statusDescription: String = this.javaClass.getSimpleName()

    fun checkData(data: ByteArray): Boolean {
        val resp = response ?: return false
        val valid = EquilPacketCodec.validatePacket(data, resp)
        if (!valid) aapsLogger.debug(LTag.PUMPCOMM, "checkData error")
        return valid
    }

    fun getEquilDevices(): String = preferences.get(EquilStringKey.Device)
    fun getEquilPassWord(): String = preferences.get(EquilStringKey.Password)
    open fun isPairStep(): Boolean = false

    fun responseCmd(equilCmdModel: EquilCmdModel, port: String?): EquilResponse {
        val result = EquilPacketCodec.buildPackets(equilCmdModel, port, reqIndex, createTime)
        reqIndex++
        return result
    }

    open fun decodeModel(): EquilCmdModel = EquilPacketCodec.parseModel(requireNotNull(response))

    fun isEnd(b: Byte): Boolean = EquilPacketCodec.isEnd(b)

    fun getIndex(b: Byte): Int = EquilPacketCodec.getIndex(b)

    fun convertString(input: String): String {
        val sb = StringBuilder()
        for (ch in input.toCharArray()) {
            sb.append("0").append(ch)
        }
        return sb.toString()
    }

    companion object {

        const val DEFAULT_PORT: String = "0F0F"
        var reqIndex: Int = 0
        var pumpReqIndex: Int = 10
        var rspIndex: Int = -1
    }
}
