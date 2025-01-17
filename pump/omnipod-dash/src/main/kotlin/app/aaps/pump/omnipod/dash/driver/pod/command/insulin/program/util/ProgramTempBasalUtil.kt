package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util

import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.TempBasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.util.MessageUtil
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object ProgramTempBasalUtil {

    fun mapTenthPulsesPerSlotToLongInsulinProgramElements(tenthPulsesPerSlot: ShortArray): List<BasalInsulinProgramElement> {
        return ProgramBasalUtil.mapTenthPulsesPerSlotToLongInsulinProgramElements(tenthPulsesPerSlot) { startSlotIndex: Byte, numberOfSlots: Byte, totalTenthPulses: Short ->
            TempBasalInsulinProgramElement(
                startSlotIndex,
                numberOfSlots,
                totalTenthPulses
            )
        }
    }

    fun mapTempBasalToTenthPulsesPerSlot(durationInSlots: Int, rateInUnitsPerHour: Double): ShortArray {
        val pulsesPerHour = (rateInUnitsPerHour * 20).roundToInt().toShort()
        val tenthPulsesPerSlot = ShortArray(durationInSlots)
        val tenthPulsesPerSlotShort = if (pulsesPerHour == 0.toShort() && durationInSlots > 4) {
            // Workaround for 0.0 U/h long temp basals being cancelled by pod
            // This will result in a 0.01 U/h temp basal for 0temps > 120 minutes
            1.toShort()
        } else {
            (roundToHalf(pulsesPerHour / 2.0) * 10).toInt().toShort()
        }
        for (i in tenthPulsesPerSlot.indices) {
            tenthPulsesPerSlot[i] = tenthPulsesPerSlotShort
        }
        return tenthPulsesPerSlot
    }

    private fun roundToHalf(d: Double): Double {
        return ((d * 10.0).toInt().toShort() / 5 * 5).toShort().toDouble() / 10.0
    }

    fun mapTempBasalToPulsesPerSlot(durationInSlots: Byte, rateInUnitsPerHour: Double): ShortArray {
        val pulsesPerHour = (rateInUnitsPerHour * 20).roundToInt().toShort()
        val pulsesPerSlot = ShortArray(durationInSlots.toInt())
        var remainingPulse = false
        var i = 0
        while (durationInSlots > i) {
            pulsesPerSlot[i] = (pulsesPerHour / 2).toShort()
            if (pulsesPerHour % 2 == 1) { // Do extra alternate pulse
                if (remainingPulse) {
                    pulsesPerSlot[i] = (pulsesPerSlot[i] + 1).toShort()
                }
                remainingPulse = !remainingPulse
            }
            i++
        }
        return pulsesPerSlot
    }

    fun calculateChecksum(totalNumberOfSlots: Byte, pulsesInFirstSlot: Short, pulsesPerSlot: ShortArray?): Short {
        val buffer = ByteBuffer.allocate(1 + 2 + 2 + 2 * pulsesPerSlot!!.size)
            .put(totalNumberOfSlots)
            .putShort(0x3840.toShort())
            .putShort(pulsesInFirstSlot)
        for (pulses in pulsesPerSlot) {
            buffer.putShort(pulses)
        }
        return MessageUtil.calculateChecksum(buffer.array())
    }

    fun mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot: ShortArray?): List<ShortInsulinProgramElement> =
        ProgramBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot)
}
