package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.extension.toLong
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject
import kotlin.experimental.and

class NotificationPacket(val injector: HasAndroidInjector) {

    /**
     * This is a bit of a special packet, as it is not a command packet
     * but a notification packet. It is sent by the pump to the phone
     * when the pump has a notification to send.
     *
     * Notifications are sent regualary, regardless of the pump state.
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

    companion object {

        private const val NOTIF_STATE_START = 0
        private const val NOTIF_STATE_END = NOTIF_STATE_START + 1

        private const val MASK_SUSPEND = 0x01
        private const val MASK_NORMAL_BOLUS = 0x02
        private const val MASK_EXTENDED_BOLUS = 0x04
        private const val MASK_BASAL = 0x08

        private const val MASK_SETUP = 0x10
        private const val MASK_RESERVOIR = 0x20
        private const val MASK_LIFE_TIME = 0x40
        private const val MASK_BATTERY = 0x80

        private const val MASK_STORAGE = 0x100
        private const val MASK_ALARM = 0x200
        private const val MASK_START_TIME = 0x400
        private const val MASK_UNKNOWN_1 = 0x800

        private const val MASK_UNUSED_CGM = 0x1000
        private const val MASK_UNUSED_COMMAND_CONFIRM = 0x2000
        private const val MASK_UNUSED_AUTO_STATUS = 0x4000
        private const val MASK_UNUSED_LEGACY = 0x8000
    }

    init {
        injector.androidInjector().inject(this)
    }

    fun handleNotification(notification: ByteArray) {
        val state = MedtrumPumpState.fromByte(notification[0])
        aapsLogger.debug(LTag.PUMPCOMM, "Notification state: $state, current state: ${medtrumPump.pumpState}")

        // TODO: Do we need to emit an event on state change?
        medtrumPump.pumpState = state

        if (notification.size > NOTIF_STATE_END) {
            handleMaskedMessage(notification.copyOfRange(NOTIF_STATE_END, notification.size))
        }
    }

    /**
     * Handle a message with a field mask, can be used by other packets as well
     */
    fun handleMaskedMessage(data: ByteArray) {
        val fieldMask = data.copyOfRange(0, 2).toInt()
        var offset = 2

        aapsLogger.debug(LTag.PUMPCOMM, "Message field mask: $fieldMask")

        if (fieldMask and MASK_SUSPEND != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Suspend notification received")
            medtrumPump.suspendTime = data.copyOfRange(offset, offset + 4).toLong()
            aapsLogger.debug(LTag.PUMPCOMM, "Suspend time: ${medtrumPump.suspendTime}")
            offset += 4
        }

        if (fieldMask and MASK_NORMAL_BOLUS != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Normal bolus notification received")
            var bolusData = data.copyOfRange(offset, offset + 1).toInt()
            var bolusType = bolusData and 0x7F
            var bolusCompleted = (bolusData shr 7) and 0x01 // TODO: Check for other flags here :)
            var bolusDelivered = data.copyOfRange(offset + 1, offset + 3).toInt() * 0.05
            // TODO Sync bolus flow:
            // If bolus is known add status
            // If bolus is not known start read bolus
            // When bolus is completed, remove bolus from medtrumPump
            aapsLogger.debug(LTag.PUMPCOMM, "Bolus type: $bolusType, bolusData: $bolusData bolus completed: $bolusCompleted, bolus delivered: $bolusDelivered")
            offset += 3
        }

        if (fieldMask and MASK_EXTENDED_BOLUS != 0) {
            aapsLogger.error(LTag.PUMPCOMM, "Extended bolus notification received, extended bolus not supported!")
            // TODO Handle error and stop pump if this happens
            offset += 3
        }

        if (fieldMask and MASK_BASAL != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Basal notification received")
            val basalType = enumValues<BasalType>()[data.copyOfRange(offset, offset + 1).toInt()]
            var basalSequence = data.copyOfRange(offset + 1, offset + 3).toInt()
            var basalPatchId = data.copyOfRange(offset + 3, offset + 5).toLong()
            var basalTime = MedtrumTimeUtil().convertPumpTimeToSystemTimeMillis(data.copyOfRange(offset + 5, offset + 9).toLong())
            var basalRateAndDelivery = data.copyOfRange(offset + 9, offset + 12).toInt()
            var basalRate = (basalRateAndDelivery and 0xFFF) * 0.05
            var basalDelivery = (basalRateAndDelivery shr 12) * 0.05
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "Basal type: $basalType, basal sequence: $basalSequence, basal patch id: $basalPatchId, basal time: $basalTime, basal rate: $basalRate, basal delivery: $basalDelivery"
            )
            medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalTime)
            offset += 12
        }

        if (fieldMask and MASK_SETUP != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Setup notification received")
            medtrumPump.primeProgress = data.copyOfRange(offset, offset + 1).toInt()
            aapsLogger.debug(LTag.PUMPCOMM, "Prime progress: ${medtrumPump.primeProgress}")
            offset += 1
        }

        if (fieldMask and MASK_RESERVOIR != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Reservoir notification received")
            medtrumPump.reservoir = data.copyOfRange(offset, offset + 2).toInt() * 0.05
            aapsLogger.debug(LTag.PUMPCOMM, "Reservoir: ${medtrumPump.reservoir}")
            offset += 2
        }

        if (fieldMask and MASK_LIFE_TIME != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Life time notification received")
            // TODO Check if timezone offset needs to be added
            medtrumPump.patchAge = data.copyOfRange(offset, offset + 4).toLong()
            aapsLogger.debug(LTag.PUMPCOMM, "Patch age: ${medtrumPump.patchAge}")
            offset += 4
        }

        if (fieldMask and MASK_BATTERY != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Battery notification received")
            var parameter = data.copyOfRange(offset, offset + 3).toInt()
            // Precision for voltage A is a guess, voltage B is the important one, threshold: < 2.64
            medtrumPump.batteryVoltage_A = (parameter and 0xFFF) / 512.0
            medtrumPump.batteryVoltage_B = (parameter shr 12) / 512.0
            aapsLogger.debug(LTag.PUMPCOMM, "Battery voltage A: ${medtrumPump.batteryVoltage_A}, battery voltage B: ${medtrumPump.batteryVoltage_B}")
            offset += 3
        }

        if (fieldMask and MASK_STORAGE != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Storage notification received")
            // TODO, trigger check for new sequence?
            val sequence = data.copyOfRange(offset, offset + 2).toInt()
            if (sequence > medtrumPump.currentSequenceNumber) {
                medtrumPump.currentSequenceNumber = sequence
            }
            val patchId = data.copyOfRange(offset + 2, offset + 4).toLong()
            if (patchId != medtrumPump.patchId) {
                aapsLogger.error(LTag.PUMPCOMM, "handleMaskedMessage: WTF? We got wrong patch id!")
                // TODO: We should terminate session or stop patch here? or at least throw error? THis can be thrown during activation process though
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Last known sequence number: ${medtrumPump.currentSequenceNumber}, patch id: ${patchId}")
            offset += 4
        }

        if (fieldMask and MASK_ALARM != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Alarm notification received")
            // Set only flags here, Alarms will be picked up by the state change
            medtrumPump.alarmFlags = data.copyOfRange(offset, offset + 2).toInt()
            medtrumPump.alarmParameter = data.copyOfRange(offset + 2, offset + 4).toInt()
            aapsLogger.debug(LTag.PUMPCOMM, "Alarm flags: ${medtrumPump.alarmFlags}, alarm parameter: ${medtrumPump.alarmParameter}")
            offset += 4
        }

        if (fieldMask and MASK_START_TIME != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Start time notification received")
            medtrumPump.patchStartTime = MedtrumTimeUtil().convertPumpTimeToSystemTimeMillis(data.copyOfRange(offset, offset + 4).toLong())
            aapsLogger.debug(LTag.PUMPCOMM, "Patch start time: ${medtrumPump.patchStartTime}")
            offset += 4
        }

        if (fieldMask and MASK_UNKNOWN_1 != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Unknown 1 notification received, not handled!")
        }

        if (fieldMask and MASK_UNUSED_CGM != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Unused CGM notification received, not handled!")
        }

        if (fieldMask and MASK_UNUSED_COMMAND_CONFIRM != 0) {
            // This one is a warning, as this happens we need to know about it, and maybe implement
            aapsLogger.warn(LTag.PUMPCOMM, "Unused command confirm notification received, not handled!")
        }

        if (fieldMask and MASK_UNUSED_AUTO_STATUS != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Unused auto status notification received, not handled!")
        }

        if (fieldMask and MASK_UNUSED_LEGACY != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Unused legacy notification received, not handled!")
        }
    }
}
