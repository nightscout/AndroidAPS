package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.Crc
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.Locale

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
        requireNotNull(response).let { response ->
            if (response.send.isNotEmpty()) {
                val preData = response.send[response.send.size - 1].array()
                val index = data[3].toInt() and 0xff
                val preIndex = preData[3].toInt() and 0xff
                if (index == preIndex) {
                    aapsLogger.debug(LTag.PUMPCOMM, "checkData error ")
                    return false
                }
            }
            val crc = data[5].toInt() and 0xff
            val crc1 = Crc.crc8Maxim(data.copyOfRange(0, 5))
            if (crc != crc1) {
                aapsLogger.debug(LTag.PUMPCOMM, "checkData crc error")
                return false
            }
            return true
        }
    }

    fun getEquilDevices(): String = preferences.get(EquilStringKey.Device)
    fun getEquilPassWord(): String = preferences.get(EquilStringKey.Password)
    open fun isPairStep(): Boolean = false

    fun responseCmd(equilCmdModel: EquilCmdModel, port: String?): EquilResponse {
        val allData = StringBuilder()
        allData.append(port)
        allData.append(equilCmdModel.tag)
        allData.append(equilCmdModel.iv)
        allData.append(equilCmdModel.ciphertext)
        var allByte = Utils.hexStringToBytes(allData.toString())
        val crc1 = Crc.getCRC(allByte)
        allByte = Utils.hexStringToBytes(allData.toString())
        var byteIndex = 0
        var lastLen = 0
        val index: Int = if ((allByte.size - 8) % 10 == 0) 1
        else 2

        val equilResponse = EquilResponse(createTime)
        val maxLen = up1(((allByte.size - 8) / 10).toDouble()) + index
        for (i in 0 until maxLen) {
            var buffer = ByteBuffer.allocate(16)
            if (i > 0 && lastLen < 10) {
                buffer = ByteBuffer.allocate(6 + lastLen)
            }
            buffer.put(0x00.toByte())
            buffer.put(0x00.toByte())
            if (i == maxLen - 1) {
                buffer.put((6 + lastLen).toByte())
                buffer.put((10 * i).toByte())
                buffer.put(toNewEndConf(reqIndex.toByte()))
            } else {
                buffer.put(0x10.toByte())
                buffer.put((10 * i).toByte())
                buffer.put(toNewStart(reqIndex.toByte()))
            }
            val crcArray = ByteArray(5)
            System.arraycopy(buffer.array(), 0, crcArray, 0, 5)
            buffer.put(Crc.crc8Maxim(crcArray).toByte())
            if (i == 0) {
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(crc1[1])
                buffer.put(crc1[0])
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
                buffer.put(allByte[byteIndex])
                byteIndex++
            } else {
                if (lastLen < 10) {
                    (0 until lastLen).forEach {
                        buffer.put(allByte[byteIndex])
                        byteIndex++
                    }
                } else {
                    (0..9).forEach {
                        buffer.put(allByte[byteIndex])
                        byteIndex++
                    }
                }
            }
            lastLen = allByte.size - byteIndex
            equilResponse.add(buffer)
        }
        reqIndex++
        return equilResponse
    }

    open fun decodeModel(): EquilCmdModel {
        requireNotNull(response).let { response ->
            val equilCmdModel = EquilCmdModel()
            val list: MutableList<Byte?> = ArrayList()
            var index = 0
            for (b in response.send) {
                if (index == 0) {
                    val bs = b.array()
                    for (i in bs.size - 4 until bs.size) list.add(bs[i])
                    val codeByte = byteArrayOf(bs[10], bs[11])
                    equilCmdModel.code = Utils.bytesToHex(codeByte)
                } else {
                    val bs = b.array()
                    for (i in 6 until bs.size) {
                        list.add(bs[i])
                    }
                }
                index++
            }
            val list1 = list.subList(0, 16)
            val list2 = list.subList(16, 12 + 16)
            val list3 = list.subList(12 + 16, list.size)
            equilCmdModel.iv = Utils.bytesToHex(list2).lowercase(Locale.getDefault())
            equilCmdModel.tag = Utils.bytesToHex(list1).lowercase(Locale.getDefault())
            equilCmdModel.ciphertext = Utils.bytesToHex(list3).lowercase(Locale.getDefault())
            return equilCmdModel
        }
    }

    // 清除指定位（设置为0）
    fun toNewStart(number: Byte): Byte = (number.toInt() and (1 shl 7).inv()).toByte()

    // 清除指定位（设置为0）
    fun toNewEndConf(number: Byte): Byte = (number.toInt() or (1 shl 7)).toByte()

    fun isEnd(b: Byte): Boolean = getBit(b, 7) == 1

    fun getIndex(b: Byte): Int = (b.toInt() and 63) // 提取前6位并右移2位

    fun getBit(b: Byte, i: Int): Int = (b.toInt() shr i) and 0x1

    fun convertString(input: String): String {
        val sb = StringBuilder()
        for (ch in input.toCharArray()) {
            sb.append("0").append(ch)
        }
        return sb.toString()
    }

    fun up1(value: Double): Int {
        val bg = BigDecimal(value)
        return bg.setScale(0, RoundingMode.UP).toInt()
    }

    companion object {

        const val DEFAULT_PORT: String = "0F0F"
        var reqIndex: Int = 0
        var pumpReqIndex: Int = 10
        var rspIndex: Int = -1
    }
}
