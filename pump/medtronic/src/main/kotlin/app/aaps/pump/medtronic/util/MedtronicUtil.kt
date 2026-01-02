package app.aaps.pump.medtronic.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.medtronic.data.dto.ClockDTO
import app.aaps.pump.medtronic.data.dto.PumpSettingDTO
import app.aaps.pump.medtronic.data.dto.RLHistoryItemMedtronic
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.MedtronicNotificationType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.InvalidParameterException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.or
import kotlin.math.abs

/**
 * Created by andy on 5/9/18.
 */
@Singleton
class MedtronicUtil @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rileyLinkUtil: RileyLinkUtil,
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val uiInteraction: UiInteraction
) {

    @Suppress("PrivatePropertyName")
    private val ENVELOPE_SIZE = 4 // 0xA7 S1 S2 S3 CMD PARAM_COUNT [PARAMS]

    //private MedtronicDeviceType medtronicPumpModel;
    private var currentCommand: MedtronicCommandType? = null
    var settings: Map<String, PumpSettingDTO>? = null

    @Suppress("PrivatePropertyName")
    private val BIG_FRAME_LENGTH = 65

    //private val doneBit = 1 shl 7
    var pumpTime: ClockDTO? = null
    var gsonInstance: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    // fun getTimeFrom30MinInterval(interval: Int): LocalTime {
    //     return if (interval % 2 == 0) {
    //         LocalTime(interval / 2, 0)
    //     } else {
    //         LocalTime((interval - 1) / 2, 30)
    //     }
    // }

    // fun decodeBasalInsulin(i: Int, j: Int): Double {
    //     return decodeBasalInsulin(makeUnsignedShort(i, j))
    // }

    // fun decodeBasalInsulin(i: Int): Double {
    //     return i.toDouble() / 40.0
    // }

    // fun getBasalStrokes(amount: Double): ByteArray {
    //     return getBasalStrokes(amount, false)
    // }

    // fun getBasalStrokesInt(amount: Double): Int {
    //     return getStrokesInt(amount, 40)
    // }

    fun getBolusStrokes(amount: Double): ByteArray {
        val strokesPerUnit = medtronicPumpStatus.medtronicDeviceType.bolusStrokes
        val length: Int
        val scrollRate: Int
        if (strokesPerUnit >= 40) {
            length = 2

            // 40-stroke pumps scroll faster for higher unit values
            scrollRate = if (amount > 10) 4 else if (amount > 1) 2 else 1
        } else {
            length = 1
            scrollRate = 1
        }
        val strokes = (amount * (strokesPerUnit * 1.0 / (scrollRate * 1.0))).toInt() * scrollRate
        return ByteUtil.fromHexString(String.format("%02x%0" + 2 * length + "x", length, strokes)) ?: throw InvalidParameterException()
    }

    // fun createCommandBody(input: ByteArray): ByteArray {
    //     return ByteUtil.concat(input.size.toByte(), input)
    // }

    // fun sendNotification(notificationType: MedtronicNotificationType, rh: ResourceHelper) {
    //     uiInteraction.addNotification(
    //         notificationType.notificationType,
    //         rh.gs(notificationType.resourceId),
    //         notificationType.notificationUrgency
    //     )
    // }

    fun sendNotification(notificationType: MedtronicNotificationType, rh: ResourceHelper, vararg parameters: Any?) {
        uiInteraction.addNotification(
            notificationType.notificationType,
            rh.gs(notificationType.resourceId, *parameters),
            notificationType.notificationUrgency
        )
    }

    fun dismissNotification(notificationType: MedtronicNotificationType, rxBus: RxBus) {
        rxBus.send(EventDismissNotification(notificationType.notificationType))
    }

    fun buildCommandPayload(rileyLinkServiceData: RileyLinkServiceData, commandType: MedtronicCommandType, parameters: ByteArray?): ByteArray {
        return buildCommandPayload(rileyLinkServiceData, commandType.commandCode, parameters)
    }

    private fun buildCommandPayload(rileyLinkServiceData: RileyLinkServiceData, commandType: Byte, parameters: ByteArray?): ByteArray {
        // A7 31 65 51 C0 00 52
        val commandLength = (if (parameters == null) 2 else 2 + parameters.size).toByte()
        val sendPayloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + commandLength) // + CRC_SIZE
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN)
        val serialNumberBCD = rileyLinkServiceData.pumpIDBytes
        sendPayloadBuffer.put(0xA7.toByte())
        sendPayloadBuffer.put(serialNumberBCD[0])
        sendPayloadBuffer.put(serialNumberBCD[1])
        sendPayloadBuffer.put(serialNumberBCD[2])
        sendPayloadBuffer.put(commandType)
        if (parameters == null) {
            sendPayloadBuffer.put(0x00.toByte())
        } else {
            sendPayloadBuffer.put(parameters.size.toByte()) // size
            for (`val` in parameters) {
                sendPayloadBuffer.put(`val`)
            }
        }
        val payload = sendPayloadBuffer.array()
        aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "buildCommandPayload [%s]", ByteUtil.shortHexString(payload)))

        // int crc = computeCRC8WithPolynomial(payload, 0, payload.length - 1);

        // LOG.info("crc: " + crc);

        // sendPayloadBuffer.put((byte) crc);
        return sendPayloadBuffer.array()
    }

    // Note: at the moment supported only for 24 items, if you will use it for more than
    // that you will need to add
    fun getBasalProfileFrames(data: ByteArray): List<List<Byte>> {
        var done = false
        var start = 0
        var frame = 1
        val frames: MutableList<List<Byte>> = ArrayList()
        var lastFrame = false
        do {
            var frameLength = BIG_FRAME_LENGTH - 1
            if (start + frameLength > data.size) {
                frameLength = data.size - start
            }

            // System.out.println("Framelength: " + frameLength);
            val substring = ByteUtil.substring(data, start, frameLength)

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));
            // System.out.println("Subarray Lenths: " + substring.length);
            val frameData = ByteUtil.getListFromByteArray(substring).toMutableList()
            if (isEmptyFrame(frameData)) {
                var b = frame.toByte()
                // b |= 0x80;
                b = b or 128.toByte()
                // b |= doneBit;
                frameData.add(0, b)
                checkAndAppendLastFrame(frameData)
                lastFrame = true
                done = true
            } else {
                frameData.add(0, frame.toByte())
            }

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));
            frames.add(frameData)
            frame++
            start += BIG_FRAME_LENGTH - 1
            if (start >= data.size) {
                done = true
            }
        } while (!done)
        if (!lastFrame) {
            val frameData: MutableList<Byte> = ArrayList()
            var b = frame.toByte()
            b = b or 128.toByte()
            // b |= doneBit;
            frameData.add(b)
            checkAndAppendLastFrame(frameData)
            frames.add(frameData)
        }
        return frames
    }

    private fun checkAndAppendLastFrame(frameData: MutableList<Byte>) {
        if (frameData.size == BIG_FRAME_LENGTH) return
        val missing = BIG_FRAME_LENGTH - frameData.size
        for (i in 0 until missing) {
            frameData.add(0x00.toByte())
        }
    }

    private fun isEmptyFrame(frameData: List<Byte>): Boolean {
        for (frameDateEntry in frameData) {
            if (frameDateEntry.toInt() != 0x00) {
                return false
            }
        }
        return true
    }

    var isModelSet: Boolean = false
    // get() = medtronicPumpStatus.medtronicDeviceType != null

    var medtronicPumpModel: MedtronicDeviceType
        get() = medtronicPumpStatus.medtronicDeviceType
        set(medtronicPumpModel) {
            medtronicPumpStatus.medtronicDeviceType = medtronicPumpModel
        }

    fun getCurrentCommand(): MedtronicCommandType? {
        return currentCommand
    }

    fun setCurrentCommand(currentCommandIn: MedtronicCommandType?) {
        this.currentCommand = currentCommandIn
        if (currentCommand != null) rileyLinkUtil.rileyLinkHistory.add(RLHistoryItemMedtronic(currentCommandIn!!))
    }

    var pageNumber = 0
    var frameNumber: Int? = null

    fun setCurrentCommand(currentCommand: MedtronicCommandType, pageNumber: Int, frameNumber: Int?) {
        this.pageNumber = pageNumber
        this.frameNumber = frameNumber
        if (this.currentCommand !== currentCommand) {
            setCurrentCommand(currentCommand)
        }
        rxBus.send(EventRileyLinkDeviceStatusChange(medtronicPumpStatus.pumpDeviceState))
    }

    companion object {

        const val isLowLevelDebug = true
        fun getIntervalFromMinutes(minutes: Int): Int {
            return minutes / 30
        }

        fun makeUnsignedShort(b2: Int, b1: Int): Int {
            return ((b2 and 0xff) shl 8) or (b1 and 0xff)
        }

        fun getByteArrayFromUnsignedShort(shortValue: Int, returnFixedSize: Boolean): ByteArray {
            val highByte = (shortValue shr 8 and 0xFF).toByte()
            val lowByte = (shortValue and 0xFF).toByte()
            return if (highByte > 0) {
                createByteArray(highByte, lowByte)
            } else {
                if (returnFixedSize) createByteArray(highByte, lowByte) else createByteArray(lowByte)
            }
        }

        fun createByteArray(vararg data: Byte): ByteArray {
            return data
        }

        fun createByteArray(data: List<Byte>): ByteArray {
            val array = ByteArray(data.size)
            for (i in data.indices) {
                array[i] = data[i]
            }
            return array
        }

        fun getBasalStrokes(amount: Double, returnFixedSize: Boolean): ByteArray {
            return getStrokes(amount, 40, returnFixedSize)
        }

        @Suppress("SameParameterValue")
        private fun getStrokes(amount: Double, strokesPerUnit: Int, returnFixedSize: Boolean): ByteArray {
            val strokes = getStrokesInt(amount, strokesPerUnit)
            return getByteArrayFromUnsignedShort(strokes, returnFixedSize)
        }

        private fun getStrokesInt(amount: Double, strokesPerUnit: Int): Int {
            //var length = 1
            var scrollRate = 1
            if (strokesPerUnit >= 40) {
                //    length = 2

                // 40-stroke pumps scroll faster for higher unit values
                if (amount > 10) scrollRate = 4 else if (amount > 1) scrollRate = 2
            }
            var strokes = (amount * (strokesPerUnit / (scrollRate * 1.0))).toInt()
            strokes *= scrollRate
            return strokes
        }

        fun isSame(d1: Double?, d2: Double?): Boolean {
            d1 ?: return false
            d2 ?: return false
            val diff = d1 - d2
            return abs(diff) <= 0.000001
        }
    }

}