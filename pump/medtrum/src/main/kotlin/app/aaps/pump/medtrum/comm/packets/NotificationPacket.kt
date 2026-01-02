package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.comm.enums.AlarmState
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.extension.toInt
import app.aaps.pump.medtrum.extension.toLong
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class NotificationPacket(val injector: HasAndroidInjector) {

    /**
     * This is a bit of a special packet, as it is not a command packet
     * but a notification packet. It is sent by the pump to the phone
     * when the pump has a notification to send.
     *
     * Notifications are sent regularly, regardless of the pump state.
     *
     * There can be multiple messages in one packet, this is noted by the fieldMask.
     *
     * Byte 1: State (Handle a state change directly? before analyzing further?)
     * Byte 2-3: FieldMask (BitMask which tells the fields present in the message)
     * Byte 4-end : status data
     *
     * When multiple fields are in the message, the data is concatenated.
     * This kind of message can also come as a response of SynchronizePacket,
     * and can be handled here by handleMaskedMessage() as well.
     */

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var medtrumTimeUtil: MedtrumTimeUtil

    companion object {

        private const val NOTIF_STATE_START = 0
        private const val NOTIF_STATE_END = NOTIF_STATE_START + 1

        private const val MASK_SUSPEND = 0x01
        private const val MASK_NORMAL_BOLUS = 0x02
        private const val MASK_EXTENDED_BOLUS = 0x04
        private const val MASK_BASAL = 0x08

        private const val MASK_SETUP = 0x10
        private const val MASK_RESERVOIR = 0x20
        private const val MASK_START_TIME = 0x40
        private const val MASK_BATTERY = 0x80

        private const val MASK_STORAGE = 0x100
        private const val MASK_ALARM = 0x200
        private const val MASK_AGE = 0x400
        private const val MASK_MAGNETO_PLACE = 0x800

        private const val MASK_UNUSED_CGM = 0x1000
        private const val MASK_UNUSED_COMMAND_CONFIRM = 0x2000
        private const val MASK_UNUSED_AUTO_STATUS = 0x4000
        private const val MASK_UNUSED_LEGACY = 0x8000

        private const val SIZE_FIELD_MASK = 2
        private const val SIZE_SUSPEND = 4
        private const val SIZE_NORMAL_BOLUS = 3
        private const val SIZE_EXTENDED_BOLUS = 3
        private const val SIZE_BASAL = 12
        private const val SIZE_SETUP = 1
        private const val SIZE_RESERVOIR = 2
        private const val SIZE_START_TIME = 4
        private const val SIZE_BATTERY = 3
        private const val SIZE_STORAGE = 4
        private const val SIZE_ALARM = 4
        private const val SIZE_AGE = 4
        private const val SIZE_MAGNETO_PLACE = 2
        private const val SIZE_UNUSED_CGM = 5
        private const val SIZE_UNUSED_COMMAND_CONFIRM = 2
        private const val SIZE_UNUSED_AUTO_STATUS = 2
        private const val SIZE_UNUSED_LEGACY = 2
    }

    val maskHandlers: Map<Int, (ByteArray, Int) -> Int> = mapOf(
        MASK_SUSPEND to ::handleSuspend,
        MASK_NORMAL_BOLUS to ::handleNormalBolus,
        MASK_EXTENDED_BOLUS to ::handleExtendedBolus,
        MASK_BASAL to ::handleBasal,
        MASK_SETUP to ::handleSetup,
        MASK_RESERVOIR to ::handleReservoir,
        MASK_START_TIME to ::handleStartTime,
        MASK_BATTERY to ::handleBattery,
        MASK_STORAGE to ::handleStorage,
        MASK_ALARM to ::handleAlarm,
        MASK_AGE to ::handleAge,
        MASK_MAGNETO_PLACE to ::handleUnknown1,
        MASK_UNUSED_CGM to ::handleUnusedCGM,
        MASK_UNUSED_COMMAND_CONFIRM to ::handleUnusedCommandConfirm,
        MASK_UNUSED_AUTO_STATUS to ::handleUnusedAutoStatus,
        MASK_UNUSED_LEGACY to ::handleUnusedLegacy
    )

    val sizeMap = mapOf(
        MASK_SUSPEND to SIZE_SUSPEND,
        MASK_NORMAL_BOLUS to SIZE_NORMAL_BOLUS,
        MASK_EXTENDED_BOLUS to SIZE_EXTENDED_BOLUS,
        MASK_BASAL to SIZE_BASAL,
        MASK_SETUP to SIZE_SETUP,
        MASK_RESERVOIR to SIZE_RESERVOIR,
        MASK_START_TIME to SIZE_START_TIME,
        MASK_BATTERY to SIZE_BATTERY,
        MASK_STORAGE to SIZE_STORAGE,
        MASK_ALARM to SIZE_ALARM,
        MASK_AGE to SIZE_AGE,
        MASK_MAGNETO_PLACE to SIZE_MAGNETO_PLACE,
        MASK_UNUSED_CGM to SIZE_UNUSED_CGM,
        MASK_UNUSED_COMMAND_CONFIRM to SIZE_UNUSED_COMMAND_CONFIRM,
        MASK_UNUSED_AUTO_STATUS to SIZE_UNUSED_AUTO_STATUS,
        MASK_UNUSED_LEGACY to SIZE_UNUSED_LEGACY
    )

    var newPatchStartTime = 0L

    init {
        injector.androidInjector().inject(this)
    }

    fun handleNotification(notification: ByteArray) {
        val state = MedtrumPumpState.fromByte(notification[0])
        aapsLogger.debug(LTag.PUMPCOMM, "Notification state: $state, current state: ${medtrumPump.pumpState}")

        if (state != medtrumPump.pumpState) {
            aapsLogger.debug(LTag.PUMPCOMM, "State changed from ${medtrumPump.pumpState} to $state")
            medtrumPump.pumpState = state
        }

        if (notification.size > NOTIF_STATE_END + SIZE_FIELD_MASK) {
            handleMaskedMessage(notification.copyOfRange(NOTIF_STATE_END, notification.size))
        }
    }

    /**
     * Handle a message with a field mask, can be used by other packets as well
     */
    fun handleMaskedMessage(data: ByteArray): Boolean {
        val fieldMask = data.copyOfRange(0, 2).toInt()
        var offset = 2

        val expectedLength = calculateExpectedLengthBasedOnFieldMask(fieldMask)
        if (data.size < expectedLength) {
            aapsLogger.error(LTag.PUMPCOMM, "Incorrect message length. Expected at least $expectedLength bytes.")
            return false
        }

        if (!checkDataValidity(fieldMask, data)) {
            aapsLogger.error(LTag.PUMPCOMM, "Invalid data in message")
            return false
        }

        aapsLogger.debug(LTag.PUMPCOMM, "Message field mask: $fieldMask")

        for ((mask, handler) in maskHandlers) {
            if (fieldMask and mask != 0) {
                offset = handler(data, offset)
            }
        }

        return true
    }

    private fun calculateExpectedLengthBasedOnFieldMask(fieldMask: Int): Int {
        var expectedLength = SIZE_FIELD_MASK

        for ((mask, size) in sizeMap) {
            if (fieldMask and mask != 0) {
                expectedLength += size
            }
        }

        return expectedLength
    }

    private fun calculateOffset(fieldMask: Int, targetMask: Int): Int {
        var offset = SIZE_FIELD_MASK // Start after the field mask itself

        for ((mask, size) in sizeMap) {
            if (mask == targetMask) {
                // Stop when we reach the target mask
                return offset
            } else if (fieldMask and mask != 0) {
                // If the current mask is part of the field mask, add its size to the offset
                offset += size
            }
        }

        // Code should never enter here, if does, it's a bug
        throw IllegalArgumentException("Target mask not found in field mask")
    }

    private fun checkDataValidity(fieldMask: Int, data: ByteArray): Boolean {
        // Notification packet does not have crc check, so we check validity based on expected values in the packet
        if (fieldMask and MASK_NORMAL_BOLUS != 0) {
            val offset = calculateOffset(fieldMask, MASK_NORMAL_BOLUS)
            val bolusDelivered = data.copyOfRange(offset + 1, offset + 3).toInt() * 0.05
            if (bolusDelivered < 0 || bolusDelivered > 50) {
                aapsLogger.error(LTag.PUMPCOMM, "Invalid bolus delivered: $bolusDelivered")
                return false
            }
        }

        if (fieldMask and MASK_BASAL != 0) {
            val offset = calculateOffset(fieldMask, MASK_BASAL)
            val basalPatchId = data.copyOfRange(offset + 3, offset + 5).toLong()
            val basalRateAndDelivery = data.copyOfRange(offset + 9, offset + 12).toInt()
            val basalRate = (basalRateAndDelivery and 0xFFF) * 0.05
            if (medtrumPump.patchId != 0L && basalPatchId != medtrumPump.patchId) {
                aapsLogger.error(LTag.PUMPCOMM, "Mismatched patch ID: $basalPatchId vs stored patchID: ${medtrumPump.patchId}")
                return false
            }
            if (basalRate < 0 || basalRate > 40) {
                aapsLogger.error(LTag.PUMPCOMM, "Invalid basal rate: $basalRate")
                return false
            }
        }

        if (fieldMask and MASK_RESERVOIR != 0) {
            val offset = calculateOffset(fieldMask, MASK_RESERVOIR) // You need to implement calculateOffset based on your mask handling
            val reservoirValue = data.copyOfRange(offset, offset + SIZE_RESERVOIR).toInt() * 0.05
            if (reservoirValue < 0 || reservoirValue > 400) {
                aapsLogger.error(LTag.PUMPCOMM, "Invalid reservoir value: $reservoirValue")
                return false
            }
        }

        if (fieldMask and MASK_STORAGE != 0) {
            val offset = calculateOffset(fieldMask, MASK_STORAGE) // Implement calculateOffset accordingly
            val patchId = data.copyOfRange(offset + 2, offset + 4).toLong() // Assuming patch ID is at the end of the storage data
            if (medtrumPump.patchId != 0L && patchId != medtrumPump.patchId) {
                aapsLogger.error(LTag.PUMPCOMM, "Mismatched patch ID: $patchId vs stored patchID: ${medtrumPump.patchId}")
                return false
            }
        }
        return true
    }

    private fun handleSuspend(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Suspend notification received")
        medtrumPump.suspendTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(offset, offset + 4).toLong())
        aapsLogger.debug(LTag.PUMPCOMM, "Suspend time: ${medtrumPump.suspendTime}")
        return offset + SIZE_SUSPEND
    }

    private fun handleNormalBolus(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Normal bolus notification received")
        val bolusData = data.copyOfRange(offset, offset + 1).toInt()
        val bolusType = bolusData and 0x7F
        val bolusCompleted: Boolean = ((bolusData shr 7) and 0x01) != 0
        val bolusDelivered = data.copyOfRange(offset + 1, offset + 3).toInt() * 0.05
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus type: $bolusType, bolusData: $bolusData bolus completed: $bolusCompleted, bolus delivered: $bolusDelivered")
        medtrumPump.handleBolusStatusUpdate(bolusType, bolusCompleted, bolusDelivered)
        return offset + SIZE_NORMAL_BOLUS
    }

    private fun handleExtendedBolus(data: ByteArray, offset: Int): Int {
        aapsLogger.error(LTag.PUMPCOMM, "Extended bolus notification received, extended bolus not supported!")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus data: ${data.copyOfRange(offset, offset + SIZE_EXTENDED_BOLUS).toLong()}")
        return offset + SIZE_EXTENDED_BOLUS
    }

    private fun handleBasal(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Basal notification received")
        val basalType = enumValues<BasalType>()[data.copyOfRange(offset, offset + 1).toInt()]
        val basalSequence = data.copyOfRange(offset + 1, offset + 3).toInt()
        val basalPatchId = data.copyOfRange(offset + 3, offset + 5).toLong()
        val basalStartTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(offset + 5, offset + 9).toLong())
        val basalRateAndDelivery = data.copyOfRange(offset + 9, offset + 12).toInt()
        val basalRate = (basalRateAndDelivery and 0xFFF) * 0.05
        val basalDelivery = (basalRateAndDelivery shr 12) * 0.05
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "Basal type: $basalType, basal sequence: $basalSequence, basal patch id: $basalPatchId, basal time: $basalStartTime, basal rate: $basalRate, basal delivery: $basalDelivery"
        )
        // Don't spam with basal updates here, only if the running basal rate has changed, or a new basal is set
        if (medtrumPump.lastBasalRate != basalRate || medtrumPump.lastBasalStartTime != basalStartTime) {
            medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime)
        }
        return offset + SIZE_BASAL
    }

    private fun handleSetup(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Setup notification received")
        medtrumPump.primeProgress = data.copyOfRange(offset, offset + 1).toInt()
        aapsLogger.debug(LTag.PUMPCOMM, "Prime progress: ${medtrumPump.primeProgress}")
        return offset + SIZE_SETUP
    }

    private fun handleReservoir(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir notification received")
        medtrumPump.reservoir = data.copyOfRange(offset, offset + 2).toInt() * 0.05
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir: ${medtrumPump.reservoir}")
        return offset + SIZE_RESERVOIR
    }

    private fun handleStartTime(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Start time notification received")
        newPatchStartTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(offset, offset + 4).toLong())
        if (medtrumPump.patchStartTime != newPatchStartTime) {
            aapsLogger.debug(LTag.PUMPCOMM, "Patch start time changed from ${medtrumPump.patchStartTime} to $newPatchStartTime")
            medtrumPump.patchStartTime = newPatchStartTime
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Patch start time: $newPatchStartTime")
        return offset + SIZE_START_TIME
    }

    private fun handleBattery(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Battery notification received")
        val parameter = data.copyOfRange(offset, offset + 3).toInt()
        // Precision for voltage A is a guess, voltage B is the important one, threshold: < 2.64
        medtrumPump.batteryVoltage_A = (parameter and 0xFFF) / 512.0
        medtrumPump.batteryVoltage_B = (parameter shr 12) / 512.0
        aapsLogger.debug(LTag.PUMPCOMM, "Battery voltage A: ${medtrumPump.batteryVoltage_A}, battery voltage B: ${medtrumPump.batteryVoltage_B}")
        return offset + SIZE_BATTERY
    }

    private fun handleStorage(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Storage notification received")
        val sequence = data.copyOfRange(offset, offset + 2).toInt()
        if (sequence > medtrumPump.currentSequenceNumber) {
            medtrumPump.currentSequenceNumber = sequence
        }
        val patchId = data.copyOfRange(offset + 2, offset + 4).toLong()
        if (patchId != medtrumPump.patchId) {
            aapsLogger.warn(LTag.PUMPCOMM, "handleMaskedMessage: We got wrong patch id!")
            if (newPatchStartTime != 0L) {
                // This is a fallback for when the activate packet did not receive the ack but the patch activated anyway
                aapsLogger.error(LTag.PUMPCOMM, "handleMaskedMessage: Also Received start time in this packet, registering new patch id: $patchId")
                medtrumPump.handleNewPatch(patchId, sequence, newPatchStartTime)
            }
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Last known sequence number: ${medtrumPump.currentSequenceNumber}, patch id: $patchId")
        return offset + SIZE_STORAGE
    }

    private fun handleAlarm(data: ByteArray, offset: Int): Int {
        val alarmFlags = data.copyOfRange(offset, offset + 2).toInt()
        val alarmParameter = data.copyOfRange(offset + 2, offset + 4).toInt()
        aapsLogger.debug(LTag.PUMPCOMM, "Alarm notification received, Alarm flags: $alarmFlags, alarm parameter: $alarmParameter")

        // If no alarm, clear activeAlarm list
        if (alarmFlags == 0 && medtrumPump.activeAlarms.isNotEmpty()) {
            medtrumPump.clearAlarmState()
        } else if (alarmFlags != 0) {
            // Check each alarm bit
            for (i in 0..3) { // Only the first 3 flags are interesting for us, the rest we will get from the pump state
                val alarmState = AlarmState.entries[i]
                if ((alarmFlags shr i) and 1 != 0) {
                    // If the alarm bit is set, add the corresponding alarm to activeAlarms
                    if (!medtrumPump.activeAlarms.contains(alarmState)) {
                        aapsLogger.debug(LTag.PUMPCOMM, "Adding alarm $alarmState to active alarms")
                        medtrumPump.addAlarm(alarmState)
                        medtrumPump.pumpWarning = alarmState
                    }
                } else if (medtrumPump.activeAlarms.contains(alarmState)) {
                    // If the alarm bit is not set, and the corresponding alarm is in activeAlarms, remove it
                    medtrumPump.removeAlarm(alarmState)
                }
            }
        }
        return offset + SIZE_ALARM
    }

    private fun handleAge(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Age notification received")
        medtrumPump.patchAge = data.copyOfRange(offset, offset + 4).toLong()
        aapsLogger.debug(LTag.PUMPCOMM, "Patch age: ${medtrumPump.patchAge}")
        return offset + SIZE_AGE
    }

    private fun handleUnknown1(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Magneto placement notification received!")
        val magnetoPlacement = data.copyOfRange(offset, offset + 2).toInt()
        aapsLogger.debug(LTag.PUMPCOMM, "Magneto placement: $magnetoPlacement")
        return offset + SIZE_MAGNETO_PLACE
    }

    private fun handleUnusedCGM(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Unused CGM notification received, not handled!")
        aapsLogger.debug(LTag.PUMPCOMM, "Unused CGM data: ${data.copyOfRange(offset, offset + SIZE_UNUSED_CGM).toLong()}")
        return offset + SIZE_UNUSED_CGM
    }

    private fun handleUnusedCommandConfirm(data: ByteArray, offset: Int): Int {
        aapsLogger.warn(LTag.PUMPCOMM, "Unused command confirm notification received, not handled!")
        aapsLogger.debug(LTag.PUMPCOMM, "Unused command confirm data: ${data.copyOfRange(offset, offset + SIZE_UNUSED_COMMAND_CONFIRM).toLong()}")
        return offset + SIZE_UNUSED_COMMAND_CONFIRM
    }

    private fun handleUnusedAutoStatus(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Unused auto status notification received, not handled!")
        aapsLogger.debug(LTag.PUMPCOMM, "Unused auto status data: ${data.copyOfRange(offset, offset + SIZE_UNUSED_AUTO_STATUS).toLong()}")
        return offset + SIZE_UNUSED_AUTO_STATUS
    }

    private fun handleUnusedLegacy(data: ByteArray, offset: Int): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "Unused legacy notification received, not handled!")
        aapsLogger.debug(LTag.PUMPCOMM, "Unused legacy data: ${data.copyOfRange(offset, offset + SIZE_UNUSED_LEGACY).toLong()}")
        return offset + SIZE_UNUSED_LEGACY
    }
}
