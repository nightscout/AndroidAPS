package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util

import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.BasalShortInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentBasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentSlot
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.util.MessageUtil
import java.nio.ByteBuffer
import java.util.*

object ProgramBasalUtil {

    const val MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT = 1800000000
    private const val NUMBER_OF_BASAL_SLOTS: Byte = 48
    private const val MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT: Byte = 16

    fun mapTenthPulsesPerSlotToLongInsulinProgramElements(
        tenthPulsesPerSlot: ShortArray,
        insulinProgramElementFactory: (Byte, Byte, Short) -> BasalInsulinProgramElement = ::BasalInsulinProgramElement
    ): List<BasalInsulinProgramElement> {
        require(tenthPulsesPerSlot.size <= NUMBER_OF_BASAL_SLOTS) { "Basal program must contain at most 48 slots" }
        val elements: MutableList<BasalInsulinProgramElement> = ArrayList()
        var previousTenthPulsesPerSlot: Short = 0
        var numberOfSlotsInCurrentElement: Byte = 0
        var startSlotIndex: Byte = 0
        for (i in tenthPulsesPerSlot.indices) {
            if (i == 0) {
                previousTenthPulsesPerSlot = tenthPulsesPerSlot[i]
                numberOfSlotsInCurrentElement = 1
            } else if (previousTenthPulsesPerSlot != tenthPulsesPerSlot[i] || (numberOfSlotsInCurrentElement + 1) * previousTenthPulsesPerSlot > 65534) {
                elements.add(
                    insulinProgramElementFactory(
                        startSlotIndex,
                        numberOfSlotsInCurrentElement,
                        (previousTenthPulsesPerSlot * numberOfSlotsInCurrentElement).toShort()
                    )
                )
                previousTenthPulsesPerSlot = tenthPulsesPerSlot[i]
                numberOfSlotsInCurrentElement = 1
                startSlotIndex = (numberOfSlotsInCurrentElement + startSlotIndex).toByte()
            } else {
                numberOfSlotsInCurrentElement++
            }
        }
        elements.add(
            insulinProgramElementFactory(
                startSlotIndex,
                numberOfSlotsInCurrentElement,
                (previousTenthPulsesPerSlot * numberOfSlotsInCurrentElement).toShort()
            )
        )
        return elements
    }

    fun mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot: ShortArray?): List<ShortInsulinProgramElement> {
        require(pulsesPerSlot!!.size <= NUMBER_OF_BASAL_SLOTS) { "Basal program must contain at most 48 slots" }

        val elements: MutableList<ShortInsulinProgramElement> = ArrayList()
        var extraAlternatePulse = false
        var previousPulsesPerSlot: Short = 0
        var numberOfSlotsInCurrentElement: Byte = 0
        var currentTotalNumberOfSlots: Byte = 0
        while (currentTotalNumberOfSlots < pulsesPerSlot.size) {
            if (currentTotalNumberOfSlots.toInt() == 0) {
                // First slot
                previousPulsesPerSlot = pulsesPerSlot[0]
                currentTotalNumberOfSlots++
                numberOfSlotsInCurrentElement = 1
            } else if (pulsesPerSlot[currentTotalNumberOfSlots.toInt()] == previousPulsesPerSlot) {
                // Subsequent slot in element (same pulses per slot as previous slot)
                if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT) {
                    numberOfSlotsInCurrentElement++
                } else {
                    elements.add(
                        BasalShortInsulinProgramElement(
                            numberOfSlotsInCurrentElement,
                            previousPulsesPerSlot,
                            extraAlternatePulse
                        )
                    )
                    previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots.toInt()]
                    numberOfSlotsInCurrentElement = 1
                    extraAlternatePulse = false
                }
                currentTotalNumberOfSlots++
            } else if (numberOfSlotsInCurrentElement.toInt() == 1 && !extraAlternatePulse && pulsesPerSlot[currentTotalNumberOfSlots.toInt()].toInt() == previousPulsesPerSlot + 1) {
                // Second slot of segment with extra alternate pulse
                var expectAlternatePulseForNextSegment = false
                currentTotalNumberOfSlots++
                numberOfSlotsInCurrentElement++
                extraAlternatePulse = true
                while (currentTotalNumberOfSlots < pulsesPerSlot.size) {
                    // Loop rest alternate pulse segment
                    if (pulsesPerSlot[currentTotalNumberOfSlots.toInt()].toInt() == previousPulsesPerSlot + (if (expectAlternatePulseForNextSegment) 1 else 0)) {
                        // Still in alternate pulse segment
                        expectAlternatePulseForNextSegment = !expectAlternatePulseForNextSegment
                        if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT) {
                            numberOfSlotsInCurrentElement++
                            currentTotalNumberOfSlots++
                        } else {
                            // End of alternate pulse segment (no slots left in element)
                            elements.add(
                                BasalShortInsulinProgramElement(
                                    numberOfSlotsInCurrentElement,
                                    previousPulsesPerSlot,
                                    extraAlternatePulse
                                )
                            )
                            previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots.toInt()]
                            numberOfSlotsInCurrentElement = 1
                            extraAlternatePulse = false
                            currentTotalNumberOfSlots++
                            break
                        }
                    } else {
                        // End of alternate pulse segment (new number of pulses per slot)
                        elements.add(
                            BasalShortInsulinProgramElement(
                                numberOfSlotsInCurrentElement,
                                previousPulsesPerSlot,
                                extraAlternatePulse
                            )
                        )
                        previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots.toInt()]
                        numberOfSlotsInCurrentElement = 1
                        extraAlternatePulse = false
                        currentTotalNumberOfSlots++
                        break
                    }
                }
            } else if (previousPulsesPerSlot != pulsesPerSlot[currentTotalNumberOfSlots.toInt()]) {
                // End of segment (new number of pulses per slot)
                elements.add(
                    BasalShortInsulinProgramElement(
                        numberOfSlotsInCurrentElement,
                        previousPulsesPerSlot,
                        extraAlternatePulse
                    )
                )
                previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots.toInt()]
                currentTotalNumberOfSlots++
                extraAlternatePulse = false
                numberOfSlotsInCurrentElement = 1
            } else {
                throw IllegalStateException("Reached illegal point in mapBasalProgramToShortInsulinProgramElements")
            }
        }
        elements.add(
            BasalShortInsulinProgramElement(
                numberOfSlotsInCurrentElement,
                previousPulsesPerSlot,
                extraAlternatePulse
            )
        )
        return elements
    }

    fun mapBasalProgramToTenthPulsesPerSlot(basalProgram: BasalProgram): ShortArray {
        val tenthPulsesPerSlot = ShortArray(NUMBER_OF_BASAL_SLOTS.toInt())
        for (segment in basalProgram.segments) {
            for (i in segment.startSlotIndex until segment.endSlotIndex) {
                tenthPulsesPerSlot[i] = (roundToHalf(segment.getPulsesPerHour() / 2.0) * 10).toInt()
                    .toShort() // TODO Adrian: int conversion ok?
            }
        }
        return tenthPulsesPerSlot
    }

    private fun roundToHalf(d: Double): Double {
        return ((d * 10.0).toInt().toShort() / 5 * 5).toShort().toDouble() / 10.0
    }

    fun mapBasalProgramToPulsesPerSlot(basalProgram: BasalProgram): ShortArray {
        val pulsesPerSlot = ShortArray(NUMBER_OF_BASAL_SLOTS.toInt())
        for (segment in basalProgram.segments) {
            var remainingPulse = false
            for (i in segment.startSlotIndex until segment.endSlotIndex) {
                pulsesPerSlot[i] = (segment.getPulsesPerHour() / 2).toShort()
                if (segment.getPulsesPerHour() % 2 == 1) { // Do extra alternate pulse
                    if (remainingPulse) {
                        pulsesPerSlot[i] = (pulsesPerSlot[i] + 1).toShort()
                    }
                    remainingPulse = !remainingPulse
                }
            }
        }
        return pulsesPerSlot
    }

    fun calculateCurrentSlot(pulsesPerSlot: ShortArray?, currentTime: Date): CurrentSlot {
        val instance = Calendar.getInstance()
        instance.time = currentTime
        val hourOfDay = instance[Calendar.HOUR_OF_DAY]
        val minuteOfHour = instance[Calendar.MINUTE]
        val secondOfMinute = instance[Calendar.SECOND]

        val index = ((hourOfDay * 60 + minuteOfHour) / 30).toByte()
        val secondOfDay = secondOfMinute + hourOfDay * 3600 + minuteOfHour * 60
        val secondsRemaining = ((index + 1) * 1800 - secondOfDay).toShort()
        val pulsesRemaining = (pulsesPerSlot!![index.toInt()].toDouble() * secondsRemaining / 1800).toInt().toShort()
        return CurrentSlot(index, (secondsRemaining * 8).toShort(), pulsesRemaining)
    }

    fun calculateCurrentLongInsulinProgramElement(
        elements: List<BasalInsulinProgramElement>,
        currentTime: Date
    ): CurrentBasalInsulinProgramElement {
        val instance = Calendar.getInstance()
        instance.time = currentTime
        val hourOfDay = instance[Calendar.HOUR_OF_DAY]
        val minuteOfHour = instance[Calendar.MINUTE]
        val secondOfMinute = instance[Calendar.SECOND]
        val secondOfDay = secondOfMinute + hourOfDay * 3600 + minuteOfHour * 60
        var startSlotIndex = 0
        var index: Byte = 0
        for (element in elements) {
            val startTimeInSeconds = startSlotIndex * 1800
            val endTimeInSeconds = startTimeInSeconds + element.numberOfSlots * 1800
            if (secondOfDay in startTimeInSeconds until endTimeInSeconds) { // TODO Adrian Range check ok
                var totalNumberOfTenThousandthPulsesInSlot = (element.totalTenthPulses * 1000).toLong()
                if (totalNumberOfTenThousandthPulsesInSlot == 0L) {
                    totalNumberOfTenThousandthPulsesInSlot = (element.numberOfSlots * 1000).toLong()
                }
                val durationInSeconds = endTimeInSeconds - startTimeInSeconds
                val secondsPassedInCurrentSlot = secondOfDay - startTimeInSeconds
                val remainingTenThousandthPulses =
                    ((durationInSeconds - secondsPassedInCurrentSlot) / durationInSeconds.toDouble() * totalNumberOfTenThousandthPulsesInSlot).toLong()
                val delayBetweenTenthPulsesInUsec =
                    (durationInSeconds * 1000000L * 1000 / totalNumberOfTenThousandthPulsesInSlot).toInt()
                val secondsRemaining = secondsPassedInCurrentSlot % 1800
                var delayUntilNextTenthPulseInUsec = delayBetweenTenthPulsesInUsec
                for (i in 0 until secondsRemaining) {
                    delayUntilNextTenthPulseInUsec -= 1000000
                    while (delayUntilNextTenthPulseInUsec <= 0) {
                        delayUntilNextTenthPulseInUsec += delayBetweenTenthPulsesInUsec
                    }
                }
                val remainingTenthPulses =
                    ((if (remainingTenThousandthPulses % 1000 != 0L) 1 else 0) + remainingTenThousandthPulses / 1000).toShort()
                return CurrentBasalInsulinProgramElement(index, delayUntilNextTenthPulseInUsec, remainingTenthPulses)
            }
            index++
            startSlotIndex += element.numberOfSlots.toInt()
        }
        throw IllegalStateException("Could not determine current long insulin program element")
    }

    fun calculateChecksum(pulsesPerSlot: ShortArray?, currentSlot: CurrentSlot): Short {
        val buffer = ByteBuffer.allocate(1 + 2 + 2 + NUMBER_OF_BASAL_SLOTS * 2)
            .put(currentSlot.index)
            .putShort(currentSlot.pulsesRemaining)
            .putShort(currentSlot.eighthSecondsRemaining)
        for (pulses in pulsesPerSlot!!) {
            buffer.putShort(pulses)
        }
        return MessageUtil.calculateChecksum(buffer.array())
    }
}
