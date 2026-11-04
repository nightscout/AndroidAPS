package app.aaps.pump.aaruy.comm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The packets on the wire. All numbers are little endian. Values that the pump treats as unsigned are
 * kept in signed types of the same width and read with [unsigned].
 *
 * A parser that catches an exception gives back the fields it read so far, so a short packet does not
 * stop the caller. Some parsers do not catch, and the exception then goes to the frame handler.
 */
internal object AaruyParamId {

    const val BASIC_PARAM: Short = 0
    const val BASAL_SETTING: Short = 1
    const val BASAL_SETTING_TWO: Short = 2
    const val BASAL_SETTING_THREE: Short = 3
    const val BOLUS_SETTING: Short = 4
    const val SYNC_TIME: Short = 5
    const val CURRENT_STATUS: Short = 6
    const val BASAL_CONTROL: Short = 7
    const val TEMP_BASAL_CONTROL: Short = 8
    const val BOLUS_CONTROL: Short = 9
    const val TOTAL_CONTROL: Short = 10
    const val ERROR_INFO: Short = 12
    const val HISTORY_REQUEST: Short = 13
    const val HISTORY_LOG: Short = 14
    const val REAL_DATA: Short = 15
    const val CONFIRM_WARN: Short = 16
    const val REPORT_DEVICE: Short = 17
    const val PARAM_19: Short = 19
    const val HISTORY_DAILY: Short = 20
    const val SERIAL: Short = 21
    const val CLOSED_MODE: Short = 24
    const val UNBIND: Short = -3
    const val PARAM_MINUS_20: Short = -20
    const val PARAM_MINUS_21: Short = -21
}

/** Actions of the [AaruyParamId.TOTAL_CONTROL] packet. */
internal object AaruyTotalControl {

    const val CONTINUE: Byte = 1
    const val REPLACE: Byte = 3
    const val PUSH_AIR: Byte = 4
    const val PUSH_POSITION: Byte = 5
    const val RESTORE_UN_RUN: Byte = 6
}

internal object AaruyPumpTime {

    /** The pump counts time in seconds from this moment. */
    const val BASE_TIME_MS = 1609430400000L
    const val DAY_SECONDS = 86400L

    fun now(): Int = ((System.currentTimeMillis() - BASE_TIME_MS) / 1000).toInt()
}

internal fun Short.unsigned(): Int = toInt() and 0xFFFF
internal fun Byte.unsigned(): Int = toInt() and 0xFF
internal fun Int.unsigned(): Long = toLong() and 0xFFFFFFFFL

/** Units to thousandths of a unit, cut and not rounded. The small addition is for values like 8.075, which give 8074.999... */
internal fun Double.toThousandths(): Short = (this * 1000.0 + 0.000001).toInt().toShort()

private fun allocate(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

internal class AaruyByteReader(data: ByteArray) {

    private val buffer: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun u8(): Int = buffer.get().toInt() and 0xFF

    fun u16(): Short = ((buffer.get().toInt() and 0xFF) or ((buffer.get().toInt() and 0xFF) shl 8)).toShort()

    fun u32(): Int =
        (buffer.get().toInt() and 0xFF) or
            ((buffer.get().toInt() and 0xFF) shl 8) or
            ((buffer.get().toInt() and 0xFF) shl 16) or
            ((buffer.get().toInt() and 0xFF) shl 24)

    companion object {

        fun slice(data: ByteArray, start: Int, length: Int): ByteArray {
            val result = ByteArray(length)
            System.arraycopy(data, start, result, 0, length)
            return result
        }

        /** Fails when the data does not end on a full chunk. */
        fun chunks(data: ByteArray, start: Int, size: Int): List<ByteArray> {
            val result = ArrayList<ByteArray>()
            var position = start
            while (position < data.size) {
                result.add(slice(data, position, size))
                position += size
            }
            return result
        }
    }
}

internal class RealDataPacket {

    var remainBattery: Byte = 0
    var bleSignal: Byte = 0

    companion object {

        fun parse(data: ByteArray): RealDataPacket {
            val reader = AaruyByteReader(data)
            return RealDataPacket().apply {
                remainBattery = reader.u8().toByte()
                bleSignal = reader.u8().toByte()
            }
        }
    }
}

internal class BasalAreaPacket {

    var startTime: Byte = 0
    var inject: Short = 0

    fun toBytes(): ByteArray = allocate(3).put(startTime).putShort(inject).array()

    companion object {

        fun parse(data: ByteArray): BasalAreaPacket {
            val reader = AaruyByteReader(data)
            return BasalAreaPacket().apply {
                startTime = reader.u8().toByte()
                inject = reader.u16()
            }
        }
    }
}

internal class HistoryRequestPacket(private val startTime: Int, private val source: Byte, private val type: Byte) {

    fun toBytes(): ByteArray = allocate(8).putInt(startTime).put(source).put(type).put(ByteArray(2)).array()
}

internal class BasalInfoPacket {

    var injectPerDay: Short = 0
    var segment: Byte = 0
    var totalArea: Byte = 0
    var areaBytes: List<ByteArray>? = null
    var areas: List<BasalAreaPacket>? = null

    fun toBytes(): ByteArray {
        val bytes = areaBytes.orEmpty()
        val buffer = allocate(4 + bytes.sumOf { it.size }).putShort(injectPerDay).put(segment).put(totalArea)
        bytes.forEach { buffer.put(it) }
        return buffer.array()
    }

    companion object {

        fun parse(data: ByteArray): BasalInfoPacket {
            val reader = AaruyByteReader(data)
            val packet = BasalInfoPacket()
            try {
                packet.injectPerDay = reader.u16()
                packet.segment = reader.u8().toByte()
                packet.totalArea = reader.u8().toByte()
                packet.areaBytes = AaruyByteReader.chunks(data, 4, 3)
                packet.areas = packet.areaBytes?.map { BasalAreaPacket.parse(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }
    }
}

internal class ReportDevicePacket {

    var settingTime: Int = 0
    var deviceType: Byte = 1
    var version: Byte = 1
    var serial: Short = 0

    fun toBytes(): ByteArray = allocate(8).putInt(settingTime).put(deviceType).put(version).putShort(serial).array()

    companion object {

        fun parse(data: ByteArray): ReportDevicePacket {
            val reader = AaruyByteReader(data)
            val packet = ReportDevicePacket()
            try {
                packet.settingTime = reader.u32()
                packet.deviceType = reader.u8().toByte()
                packet.version = reader.u8().toByte()
                packet.serial = reader.u16()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }
    }
}

internal class BasalSettingPacket {

    var settingTime: Int = 0
    var totalBasal: Byte = 0
    var curIndex: Byte = 0
    var reserved: ByteArray = ByteArray(2)
    var basalInfo: BasalInfoPacket? = null
    var basalInfoBytes: ByteArray? = null

    /** Sets [settingTime] to now. */
    fun toBytes(): ByteArray {
        val info = basalInfoBytes
        settingTime = AaruyPumpTime.now()
        val buffer = allocate(8 + (info?.size ?: 0)).putInt(settingTime).put(totalBasal).put(curIndex).put(reserved)
        info?.let { buffer.put(it) }
        return buffer.array()
    }

    companion object {

        fun parse(data: ByteArray): BasalSettingPacket {
            val reader = AaruyByteReader(data)
            val packet = BasalSettingPacket()
            try {
                packet.settingTime = reader.u32()
                packet.totalBasal = reader.u8().toByte()
                packet.curIndex = reader.u8().toByte()
                packet.reserved = AaruyByteReader.slice(data, 6, 2)
                val info = AaruyByteReader.slice(data, 8, data.size - 8)
                packet.basalInfoBytes = info
                packet.basalInfo = BasalInfoPacket.parse(info)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }
    }
}

internal class BasicParamPacket {

    var settingTime: Int = 0
    var bInit: Byte = 0
    var precision: Short = 25
    var maxBasal: Short = 2000
    var originBasal: Short = 500
    var tempBasalState: Byte = 0
    var maxBolus: Short = 10000
    var bolusStep: Byte = 2
    var bolusInc: Short = 2
    var bolusDelayState: Byte = 0
    var fastBolusStep: Short = 0
    var lowAlert: Short = 20
    var noOperateWarning: Byte = 0
    var noRunWarning: Byte = 0
    var closeInjectSound: Byte = 0
    var softNeedleType: Byte = 0
    var storageCapacity: Byte = 0
    var pumpType: Byte = 0
    var dailyLimit: Byte = 0
    var timeZoneCorrection: Byte = 0
    var reserved: ByteArray = ByteArray(2)

    fun toBytes(): ByteArray =
        allocate(32)
            .putInt(settingTime)
            .put(bInit)
            .putShort(precision)
            .putShort(maxBasal)
            .putShort(originBasal)
            .put(tempBasalState)
            .putShort(maxBolus)
            .put(bolusStep)
            .putShort(bolusInc)
            .put(bolusDelayState)
            .putShort(fastBolusStep)
            .putShort(lowAlert)
            .put(noOperateWarning)
            .put(noRunWarning)
            .put(closeInjectSound)
            .put(softNeedleType)
            .put(storageCapacity)
            .put(pumpType)
            .put(dailyLimit)
            .put(timeZoneCorrection)
            .put(reserved)
            .array()

    companion object {

        fun parse(data: ByteArray): BasicParamPacket {
            val reader = AaruyByteReader(data)
            return BasicParamPacket().apply {
                settingTime = reader.u32()
                bInit = reader.u8().toByte()
                precision = reader.u16()
                maxBasal = reader.u16()
                originBasal = reader.u16()
                tempBasalState = reader.u8().toByte()
                maxBolus = reader.u16()
                bolusStep = reader.u8().toByte()
                bolusInc = reader.u16()
                bolusDelayState = reader.u8().toByte()
                fastBolusStep = reader.u16()
                lowAlert = reader.u16()
                noOperateWarning = reader.u8().toByte()
                noRunWarning = reader.u8().toByte()
                closeInjectSound = reader.u8().toByte()
                softNeedleType = reader.u8().toByte()
                storageCapacity = reader.u8().toByte()
                pumpType = reader.u8().toByte()
                dailyLimit = reader.u8().toByte()
                timeZoneCorrection = reader.u8().toByte()
                reserved = AaruyByteReader.slice(data, 30, 2)
            }
        }
    }
}

internal class BolusSettingPacket {

    var bolus: Short = 0
    var bolusNow: Short = 0
    var bolusDelayTime: Short = 0

    /** The three reserved values are always zero. */
    fun toBytes(): ByteArray =
        allocate(16)
            .putInt(AaruyPumpTime.now())
            .putShort(bolus)
            .putShort(0)
            .putShort(0)
            .putShort(0)
            .putShort(bolusNow)
            .putShort(bolusDelayTime)
            .array()
}

internal class BasalControlPacket(private val settingTime: Int, private val action: Byte) {

    fun toBytes(): ByteArray = allocate(6).putInt(settingTime).put(action).put(0.toByte()).array()
}

/** Used for both [AaruyParamId.BOLUS_CONTROL] and [AaruyParamId.TOTAL_CONTROL]. */
internal class ControlPacket(private val settingTime: Int, private val action: Byte, private val extra: Byte = 0) {

    /** Pump protocol version 1 has three more bytes. */
    fun toBytes(protocolVersion: Int): ByteArray {
        val buffer = allocate(if (protocolVersion == 1) 8 else 5).putInt(settingTime).put(action)
        if (protocolVersion == 1) buffer.put(extra)
        return buffer.array()
    }
}

internal class TempBasalControlPacket(
    private val settingTime: Int,
    private val action: Byte,
    private val rate: Short,
    private val duration: Short
) {

    fun toBytes(): ByteArray = allocate(9).putInt(settingTime).put(action).putShort(rate).putShort(duration).array()
}

internal class CurrentStatusPacket {

    class BasalValue {

        var index: Byte = 0
        var curValue: Short = 0
        var remainTime: Short = 0
        var runningTime: Short = 0
        var basalType: Byte = 0
    }

    class BolusValue {

        var state: Byte = 0
        var curBolusNow: Short = 0
        var curBolusDelay: Short = 0
        var remainTime: Short = 0
        var bolusType: Byte = 0
    }

    class PauseValue {

        var index: Byte = 0
        var pauseOrStopTime: Int = 0
    }

    var settingTime: Int = 0
    var remainAmount: Short = 0
    var runningStatus: Byte = 0
    var basal: BasalValue? = null
    var tempBasal: BasalValue? = null
    var bolus: BolusValue? = null
    var pause: PauseValue? = null

    companion object {

        const val RUNNING_BASAL: Byte = 1
        const val RUNNING_TEMP_BASAL: Byte = 2
        const val RUNNING_BOLUS: Byte = 3
        const val RUNNING_PAUSED: Byte = 7
        const val RUNNING_STOPPED: Byte = 8

        private const val SIZE_WITHOUT_TYPE = 14

        fun parse(data: ByteArray): CurrentStatusPacket {
            val reader = AaruyByteReader(data)
            val packet = CurrentStatusPacket()
            try {
                packet.settingTime = reader.u32()
                packet.remainAmount = reader.u16()
                packet.runningStatus = reader.u8().toByte()
                when (packet.runningStatus) {
                    RUNNING_BASAL                  -> packet.basal = parseBasal(reader, data)
                    RUNNING_TEMP_BASAL             -> packet.tempBasal = parseBasal(reader, data)
                    RUNNING_BOLUS                  -> packet.bolus = parseBolus(reader, data)
                    RUNNING_PAUSED, RUNNING_STOPPED -> packet.pause = parsePause(reader)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }

        private fun parseBasal(reader: AaruyByteReader, data: ByteArray) = BasalValue().apply {
            index = reader.u8().toByte()
            curValue = reader.u16()
            remainTime = reader.u16()
            runningTime = reader.u16()
            if (data.size > SIZE_WITHOUT_TYPE) basalType = reader.u8().toByte()
        }

        private fun parseBolus(reader: AaruyByteReader, data: ByteArray) = BolusValue().apply {
            state = reader.u8().toByte()
            curBolusNow = reader.u16()
            curBolusDelay = reader.u16()
            remainTime = reader.u16()
            if (data.size > SIZE_WITHOUT_TYPE) bolusType = reader.u8().toByte()
        }

        private fun parsePause(reader: AaruyByteReader) = PauseValue().apply {
            index = reader.u8().toByte()
            pauseOrStopTime = reader.u32()
        }
    }
}

internal class ErrorInfoPacket {

    var errorType: Int = 0
    var error: Int = 0

    companion object {

        fun parse(data: ByteArray): ErrorInfoPacket {
            val reader = AaruyByteReader(data)
            val packet = ErrorInfoPacket()
            try {
                packet.errorType = reader.u8()
                packet.error = reader.u8()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }
    }
}

internal class HistoryDailyUnitPacket {

    var date: Short = 0
    var dailyTotal: Short = 0
    var dailyBolus: Short = 0

    companion object {

        fun parse(data: ByteArray): HistoryDailyUnitPacket {
            val reader = AaruyByteReader(data)
            return HistoryDailyUnitPacket().apply {
                date = reader.u16()
                dailyTotal = reader.u16()
                dailyBolus = reader.u16()
            }
        }
    }
}

internal class HistoryDailyPacket {

    var count: Byte = 0
    var units: List<HistoryDailyUnitPacket> = emptyList()

    companion object {

        fun parse(data: ByteArray): HistoryDailyPacket {
            val reader = AaruyByteReader(data)
            val packet = HistoryDailyPacket()
            packet.count = reader.u8().toByte()
            packet.units = AaruyByteReader.chunks(data, 1, 6).map { HistoryDailyUnitPacket.parse(it) }
            return packet
        }
    }
}

internal class HistoryUnitPacket {

    var time: Int = 0
    var type: Byte = 0
    var data: Short = 0
    var injectTime: Short = 0
    var delayBolus: Short = 0
    var reserved: Byte = 0

    companion object {

        fun parse(data: ByteArray): HistoryUnitPacket {
            val reader = AaruyByteReader(data)
            return HistoryUnitPacket().apply {
                time = reader.u32()
                type = reader.u8().toByte()
                this.data = reader.u16()
                injectTime = reader.u16()
                delayBolus = reader.u16()
                reserved = reader.u8().toByte()
            }
        }
    }
}

internal class HistoryLogPacket {

    var count: Short = 0
    var totalPage: Byte = 0
    var curPage: Byte = 0
    var curNum: Byte = 0
    var units: List<HistoryUnitPacket> = emptyList()

    companion object {

        fun parse(data: ByteArray): HistoryLogPacket {
            val reader = AaruyByteReader(data)
            val packet = HistoryLogPacket()
            packet.count = reader.u16()
            packet.totalPage = reader.u8().toByte()
            packet.curPage = reader.u8().toByte()
            packet.curNum = reader.u8().toByte()
            try {
                packet.units = AaruyByteReader.chunks(data, 5, 12).map { HistoryUnitPacket.parse(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return packet
        }
    }
}
