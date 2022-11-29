package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.pump.common.defs.TempBasalPair
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.Locale

/**
 * Created by geoff on 5/29/15.
 *
 * Just need a class to keep the pair together, for parcel transport.
 */
class TempBasalPair : TempBasalPair {

    /**
     * This constructor is for use with PumpHistoryDecoder
     *
     * @param rateByte
     * @param startTimeByte
     * @param isPercent
     */
    constructor(rateByte: Byte, startTimeByte: Int, isPercent: Boolean) : super() {
        val rateInt = ByteUtil.asUINT8(rateByte)
        if (isPercent) insulinRate = rateByte.toDouble() else insulinRate = rateInt * 0.025
        durationMinutes = startTimeByte * 30
        this.isPercent = isPercent
    }

    /**
     * This constructor is for use with PumpHistoryDecoder
     *
     * @param rateByte0
     * @param startTimeByte
     * @param isPercent
     */
    constructor(rateByte0: Byte, rateByte1: Byte, startTimeByte: Int, isPercent: Boolean) {
        if (isPercent) {
            insulinRate = rateByte0.toDouble()
        } else {
            insulinRate = ByteUtil.toInt(rateByte1.toInt(), rateByte0.toInt()) * 0.025
        }
        durationMinutes = startTimeByte * 30
        this.isPercent = isPercent
    }

    constructor(aapsLogger: AAPSLogger, response: ByteArray) : super() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received TempBasal response: " + ByteUtil.getHex(response))
        isPercent = response[0] == 1.toByte()
        insulinRate = if (isPercent) {
            response[1].toDouble()
        } else {
            val strokes = MedtronicUtil.makeUnsignedShort(response[2].toInt(), response[3].toInt())
            strokes / 40.0
        }
        durationMinutes = if (response.size < 6) {
            ByteUtil.asUINT8(response[4])
        } else {
            MedtronicUtil.makeUnsignedShort(response[4].toInt(), response[5].toInt())
        }
        aapsLogger.warn(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "TempBasalPair (with %d byte response): %s", response.size, toString()))
    }

    constructor(insulinRate: Double, isPercent: Boolean, durationMinutes: Int) : super(insulinRate, isPercent, durationMinutes)

    // list.add((byte) 0); // ?

    // list.add((byte) 0); // is_absolute
    // list.add((byte) 0); // percent amount
    // 3 (time) - OK
    val asRawData: ByteArray
        get() {
            val list: MutableList<Byte> = ArrayList()
            list.add(5.toByte())
            val insulinRate = MedtronicUtil.getBasalStrokes(insulinRate, true)
            val timeMin = MedtronicUtil.getIntervalFromMinutes(durationMinutes).toByte()

            // list.add((byte) 0); // ?

            // list.add((byte) 0); // is_absolute
            if (insulinRate.size == 1) list.add(0x00.toByte()) else list.add(insulinRate[0])
            list.add(insulinRate[1])
            // list.add((byte) 0); // percent amount
            list.add(timeMin) // 3 (time) - OK
            if (insulinRate.size == 1) list.add(0x00.toByte()) else list.add(insulinRate[0])
            list.add(insulinRate[1])
            return MedtronicUtil.createByteArray(list)
        }

    val isCancelTBR: Boolean
        get() = MedtronicUtil.isSame(insulinRate, 0.0) && durationMinutes == 0

    val isZeroTBR: Boolean
        get() = MedtronicUtil.isSame(insulinRate, 0.0) && durationMinutes != 0

    val description: String
        get() {
            if (isCancelTBR) {
                return "Cancel TBR"
            }
            return if (isPercent) {
                String.format(Locale.ENGLISH, "Rate: %.0f%%, Duration: %d min", insulinRate, durationMinutes)
            } else {
                String.format(Locale.ENGLISH, "Rate: %.3f U, Duration: %d min", insulinRate, durationMinutes)
            }
        }

    override fun toString(): String {
        return ("TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent="
            + isPercent + "]")
    }
}