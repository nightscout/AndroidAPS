package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.comm.MessageOriginalNames.getName
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.utils.CRC.getCrc16
import dagger.android.HasAndroidInjector
import org.joda.time.DateTime
import org.joda.time.IllegalInstantException
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

/*
 *  00  01   02  03   04   05  06
 *
 *  7E  7E  len  F1  CMD  SUB data CRC CRC 2E  2E
 */
open class MessageBase(injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Inject lateinit var uiInteraction: UiInteraction

    var injector: HasAndroidInjector
    var buffer = ByteArray(512)
    private var position = 6
    var isReceived = false
    var failed = false

    fun setCommand(cmd: Int) {
        buffer[4] = (cmd shr 8 and 0xFF).toByte()
        buffer[5] = (cmd and 0xFF).toByte()
    }

    fun addParamByte(data: Byte) {
        buffer[position++] = data
    }

    fun addParamInt(data: Int) {
        buffer[position++] = (data shr 8 and 0xFF).toByte()
        buffer[position++] = (data and 0xFF).toByte()
    }

    fun addParamDate(date: GregorianCalendar) {
        addParamByte((date[Calendar.YEAR] - 1900 - 100).toByte())
        addParamByte((date[Calendar.MONTH] + 1).toByte())
        addParamByte(date[Calendar.DAY_OF_MONTH].toByte())
        addParamByte(date[Calendar.HOUR_OF_DAY].toByte())
        addParamByte(date[Calendar.MINUTE].toByte())
    }

    fun addParamDateTime(date: GregorianCalendar) {
        addParamByte((date[Calendar.YEAR] - 1900 - 100).toByte())
        addParamByte((date[Calendar.MONTH] + 1).toByte())
        addParamByte(date[Calendar.DAY_OF_MONTH].toByte())
        addParamByte(date[Calendar.HOUR_OF_DAY].toByte())
        addParamByte(date[Calendar.MINUTE].toByte())
        addParamByte(date[Calendar.SECOND].toByte())
    }

    fun addParamDateTimeReversed(timestamp: Long) {
        val date = GregorianCalendar()
        date.timeInMillis = timestamp
        addParamByte(date[Calendar.SECOND].toByte())
        addParamByte(date[Calendar.MINUTE].toByte())
        addParamByte(date[Calendar.HOUR_OF_DAY].toByte())
        addParamByte(date[Calendar.DAY_OF_MONTH].toByte())
        addParamByte((date[Calendar.MONTH] + 1).toByte())
        addParamByte((date[Calendar.YEAR] - 1900 - 100).toByte())
    }

    val rawMessageBytes: ByteArray
        get() {
            buffer[0] = 0x7E.toByte()
            buffer[1] = 0x7E.toByte()
            val length = position - 3
            buffer[2] = length.toByte()
            buffer[3] = 0xF1.toByte()
            addParamInt(getCrc16(buffer, 3, length).toInt())
            buffer[length + 5] = 0x2E.toByte()
            buffer[length + 6] = 0x2E.toByte()
            return buffer.copyOf(length + 7)
        }
    val messageName: String?
        get() = getName(command)

    open fun handleMessage(bytes: ByteArray) {
        if (bytes.size > 6) {
            val command: Int = bytes[5].toInt() and 0xFF or (bytes[4].toInt() shl 8 and 0xFF00)
            aapsLogger.debug(LTag.PUMPCOMM, "UNPROCESSED MSG: $messageName Command: ${String.format("%04X", command)} Data: ${toHexString(bytes)}")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "MISFORMATTED MSG: ${toHexString(bytes)}")
        }
    }

    open fun handleMessageNotReceived() {} // do nothing by default
    val command: Int
        get() = byteFromRawBuff(buffer, 5) or (byteFromRawBuff(buffer, 4) shl 8)

    private fun byteFromRawBuff(buff: ByteArray, offset: Int): Int {
        return buff[offset].toInt() and 0xFF
    }

    fun intFromBuff(buff: ByteArray, buffOffset: Int, length: Int): Int {
        val offset = buffOffset + 6
        when (length) {
            1 -> return byteFromRawBuff(buff, offset)
            2 -> return (byteFromRawBuff(buff, offset) shl 8) + byteFromRawBuff(buff, offset + 1)
            3 -> return (byteFromRawBuff(buff, offset + 2) shl 16) + (byteFromRawBuff(buff, offset + 1) shl 8) + byteFromRawBuff(buff, offset)
            4 -> return (byteFromRawBuff(buff, offset + 3) shl 24) + (byteFromRawBuff(buff, offset + 2) shl 16) + (byteFromRawBuff(buff, offset + 1) shl 8) + byteFromRawBuff(buff, offset)
        }
        return 0
    }

    fun dateTimeFromBuff(buff: ByteArray, offset: Int): Long {
        return DateTime(
            2000 + intFromBuff(buff, offset, 1),
            intFromBuff(buff, offset + 1, 1),
            intFromBuff(buff, offset + 2, 1),
            intFromBuff(buff, offset + 3, 1),
            intFromBuff(buff, offset + 4, 1),
            0
        ).millis
    }

    @Synchronized fun dateTimeSecFromBuff(buff: ByteArray, offset: Int): Long {
        return try {
            DateTime(
                2000 + intFromBuff(buff, offset, 1),
                intFromBuff(buff, offset + 1, 1),
                intFromBuff(buff, offset + 2, 1),
                intFromBuff(buff, offset + 3, 1),
                intFromBuff(buff, offset + 4, 1),
                intFromBuff(buff, offset + 5, 1)
            ).millis
        } catch (e: IllegalInstantException) {
            // expect
            // org.joda.time.IllegalInstantException: Illegal instant due to time zone offset transition (daylight savings time 'gap')
            // add 1 hour
            DateTime(
                2000 + intFromBuff(buff, offset, 1),
                intFromBuff(buff, offset + 1, 1),
                intFromBuff(buff, offset + 2, 1),
                intFromBuff(buff, offset + 3, 1) + 1,
                intFromBuff(buff, offset + 4, 1),
                intFromBuff(buff, offset + 5, 1)
            ).millis
        }
    }

    fun dateFromBuff(buff: ByteArray, offset: Int): Long {
        return DateTime(
            2000 + intFromBuff(buff, offset, 1),
            intFromBuff(buff, offset + 1, 1),
            intFromBuff(buff, offset + 2, 1),
            0,
            0
        ).millis
    }

    companion object {

        fun stringFromBuff(buff: ByteArray, offset: Int, length: Int): String {
            val strBuff = ByteArray(length)
            System.arraycopy(buff, offset + 6, strBuff, 0, length)
            return String(strBuff, StandardCharsets.UTF_8)
        }

        fun asciiStringFromBuff(buff: ByteArray, offset: Int, length: Int): String {
            val strBuff = ByteArray(length)
            System.arraycopy(buff, offset + 6, strBuff, 0, length)
            for (pos in 0 until length) strBuff[pos] = (strBuff[pos] + 65).toByte() // "A"
            return String(strBuff, StandardCharsets.UTF_8)
        }

        fun toHexString(buff: ByteArray): String {
            val sb = StringBuilder()
            for ((count, element) in buff.withIndex()) {
                sb.append(String.format("%02x ", element))
                if ((count + 1) % 4 == 0) sb.append(" ")
            }
            return sb.toString()
        }
    }

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
        this.injector = injector
    }
}