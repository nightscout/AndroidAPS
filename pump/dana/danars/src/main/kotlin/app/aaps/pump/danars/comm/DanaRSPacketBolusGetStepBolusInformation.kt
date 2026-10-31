package app.aaps.pump.danars.comm

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import dev.zacsweers.metro.Inject
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@Inject
class DanaRSPacketBolusGetStepBolusInformation(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val error = intFromBuff(data, 0, 1)
        val bolusType = intFromBuff(data, 1, 1)
        danaPump.initialBolusAmount = intFromBuff(data, 2, 2) / 100.0
        val hours = intFromBuff(data, 4, 1)
        val minutes = intFromBuff(data, 5, 1)
        if (isValidTime(hours, minutes)) {
            // The pump sends only the time of day. A bolus from yesterday evening must not become a time later
            // today, so a time after now is from yesterday. A few minutes after now still means today: the pump
            // clock can run a little ahead of the phone until AAPS sets it.
            val now = dateUtil.now()
            val zone = if (danaPump.usingUTC) DateTimeZone.UTC else DateTimeZone.getDefault()
            val today = DateTime(now, zone).withTime(hours, minutes, 0, 0)
            danaPump.lastBolusTime = if (today.millis > now + T.mins(5).msecs()) today.minusDays(1).millis else today.millis
            danaPump.lastBolusAmount = intFromBuff(data, 6, 2) / 100.0
        } else {
            // A pump that never gave a bolus sends FF FF as time, and the amount bytes are not valid
            danaPump.lastBolusTime = null
            danaPump.lastBolusAmount = null
        }
        danaPump.maxBolus = intFromBuff(data, 8, 2) / 100.0
        danaPump.bolusStep = intFromBuff(data, 10, 1) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "BolusType: $bolusType")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount: " + danaPump.initialBolusAmount + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeStringNullable(danaPump.lastBolusTime))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaPump.lastBolusAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaPump.maxBolus + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: " + danaPump.bolusStep + " U")
    }

    override val friendlyName: String = "BOLUS__GET_STEP_BOLUS_INFORMATION"
}