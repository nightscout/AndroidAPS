package app.aaps.pump.danars.comm

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.danars.encryption.BleEncryption
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import dev.zacsweers.metro.Inject
import kotlin.math.abs

@Inject
open class DanaRSPacketAPSHistoryEvents(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val danaPump: DanaPump,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val preferences: Preferences,
    private val pumpSync: PumpSync
) : DanaRSPacket() {

    private var from: Long = 0L

    companion object {

        var messageBuffer = arrayListOf<ByteArray>() // for reversing order of incoming messages

        // Slowest bolus speed setting is 60 s/U
        private const val SLOWEST_BOLUS_SEC_PER_UNIT = 60
        // Allowed clock difference between the two records on top of the delivery time
        private const val STEP_BOLUS_MATCH_MARGIN_MS = 60_000L

        // BASAL_RATE_CHANGE: Data[1] low 7 bits are the reason, 0x80 means the rate is in U/d
        private const val BASAL_CHANGE_UNIT_PER_DAY = 0x80
        private const val BASAL_CHANGE_REASON_MASK = 0x7F
    }

    /**
     * Dana-i2 writes a STEP_BOLUS_COMMAND record when a step bolus starts. It tells whether the bolus
     * was sent by the algorithm (SMB) or as a normal bolus. The BOLUS record with the delivered amount
     * does not have this, so the two are matched by time.
     */
    private data class StepBolusCommand(val timestamp: Long, val amount: Double, val algorithm: Boolean)

    private var stepBolusCommands: List<StepBolusCommand> = emptyList()

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS
    }

    fun with(from: Long) = this.also {
        it.from = from
        if (it.from > dateUtil.now()) {
            aapsLogger.debug(LTag.PUMPCOMM, "Asked to load from the future")
            it.from = 0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(it.from))
        danaPump.historyDoneReceived = false
        messageBuffer = arrayListOf()
    }

    override fun getRequestParams(): ByteArray {
        val date =
            if (danaPump.usingUTC) DateTime(from).withZone(DateTimeZone.UTC)
            else DateTime(from)
        val request = ByteArray(6)
        if (from == 0L) {
            request[0] = 0
            request[1] = 1
            request[2] = 1
            request[3] = 0
            request[4] = 0
            request[5] = 0
        } else {
            request[0] = (date.year - 2000 and 0xff).toByte()
            request[1] = (date.monthOfYear and 0xff).toByte()
            request[2] = (date.dayOfMonth and 0xff).toByte()
            request[3] = (date.hourOfDay and 0xff).toByte()
            request[4] = (date.minuteOfHour and 0xff).toByte()
            request[5] = (date.secondOfMinute and 0xff).toByte()
        }
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val recordCode = intFromBuff(data, 0, 1).toByte()
        // Last record
        if (recordCode == 0xFF.toByte()) {
            aapsLogger.debug(LTag.PUMPCOMM, "Last record received")

            val array: Array<ByteArray> = messageBuffer.filter { dateTime(it) > 0L }.toTypedArray()
            val sorted = array.sortedArrayWith { s1: ByteArray, s2: ByteArray -> dateTime(s1).compareTo(dateTime(s2)) }
            stepBolusCommands = sorted
                .filter { recordCode(it) == DanaPump.HistoryEntry.STEP_BOLUS_COMMAND.value }
                .map { StepBolusCommand(timestamp = dateTime(it), amount = intFromBuffMsbLsb(it, 7, 2) / 100.0, algorithm = intFromBuffMsbLsb(it, 9, 2) == 1) }
            for (index in sorted.indices) {
                val message = sorted[index]
                // workaround for RS history bug
                // sometimes TB is marked as canceled immediately
                // but on pump is running
                // at least on Model: 05 Protocol: 10 Code: 10
                if (index > 0 && recordCode(message) == DanaPump.HistoryEntry.TEMP_STOP.value) {
                    val previous = sorted[index - 1]
                    if (recordCode(previous) == DanaPump.HistoryEntry.TEMP_START.value && dateTime(message) == dateTime
                            (previous)
                    ) {
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "SKIPPING EVENT TEMP_STOP (" + recordCode(message) + ") "
                                + dateUtil.dateAndTimeString(dateTime(message)) + " (" + dateTime(message) + ")"
                        )
                        continue
                    }
                }
                processMessage(message)
            }
            danaPump.historyDoneReceived = true
        } else messageBuffer.add(data)
    }

    private fun dateTime(data: ByteArray): Long =
        if (!danaPump.usingUTC) dateTimeSecFromBuff(data, 1) // 6 bytes
        else intFromBuffMsbLsb(data, 3, 4) * 1000L

    /**
     * Bolus type from the matching STEP_BOLUS_COMMAND record, or null when there is none
     * (pumps before Dana-i2, or the command was in an earlier history read).
     */
    private fun bolusTypeFromCommand(bolusTime: Long, delivered: Double): BS.Type? =
        stepBolusCommands
            .filter { command ->
                // BOLUS is stored with the end time of the bolus, STEP_BOLUS_COMMAND with the start time.
                // A stopped bolus delivers less than was set, never more.
                val longestDelivery = (command.amount * SLOWEST_BOLUS_SEC_PER_UNIT * 1000).toLong() + STEP_BOLUS_MATCH_MARGIN_MS
                bolusTime - command.timestamp in -STEP_BOLUS_MATCH_MARGIN_MS..longestDelivery && command.amount >= delivered - 0.001
            }
            .minByOrNull { abs(bolusTime - it.timestamp) }
            ?.let { if (it.algorithm) BS.Type.SMB else BS.Type.NORMAL }

    /** Reason names from the Dana-i2 protocol spec, for the log only */
    private fun basalChangeReasonName(reason: Int): String =
        when (reason) {
            1    -> "(Power On)"
            2    -> "(User TB Start)"
            3    -> "(User TB Stop)"
            4    -> "(Command TB Start)"
            5    -> "(Command TB Stop)"
            6    -> "(Other TB Stop)"
            7    -> "(Basal Setting Change)"
            8    -> "(Change Hour)"
            9    -> "(User Change Time)"
            else -> "(Unknown)"
        }

    private fun recordCode(data: ByteArray): Int =
        if (!danaPump.usingUTC)
            intFromBuff(data, 0, 1)
        else
            intFromBuff(data, 2, 1)

    fun processMessage(data: ByteArray) {
        val recordCode = recordCode(data)
        // Last record
        if (recordCode == 0xFF) {
            return
        }
        val datetime: Long
        val param1 = intFromBuffMsbLsb(data, 7, 2)
        val param2 = intFromBuffMsbLsb(data, 9, 2)
        val pumpId: Long
        if (!danaPump.usingUTC) {
            datetime = dateTimeSecFromBuff(data, 1) // 6 bytes
            pumpId = datetime
        } else {
            datetime = intFromBuffMsbLsb(data, 3, 4) * 1000L
            val id = intFromBuffMsbLsb(data, 0, 2) // range only 1-2000
            pumpId = datetime * 2 + id
        }
        val status: String
        when (DanaPump.HistoryEntry.fromInt(recordCode)) {
            DanaPump.HistoryEntry.TEMP_START          -> {
                val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(datetime, param1.toDouble())
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = datetime,
                        rate = PumpRate(param1.toDouble()),
                        duration = T.mins(param2.toLong()).msecs(),
                        isAbsolute = false,
                        type = temporaryBasalInfo?.type,
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT TEMP_START ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Ratio: $param1% Duration: ${param2}min"
                )
                status = "TEMP_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.TEMP_STOP           -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = datetime,
                        endPumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT TEMP_STOP ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime)"
                )
                status = "TEMP_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.EXTENDED_START      -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncExtendedBolusWithPumpId(
                        timestamp = datetime,
                        rate = PumpRate(param1 / 100.0),
                        duration = T.mins(param2.toLong()).msecs(),
                        isEmulatingTB = false,
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_START ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U Duration: ${param2}min"
                )
                status = "EXTENDED_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.EXTENDED_STOP       -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = datetime,
                        endPumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_STOP ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Delivered: ${param1 / 100.0}U RealDuration: ${param2}min"
                )
                status = "EXTENDED_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.BOLUS               -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncBolusWithPumpId(
                        timestamp = datetime,
                        amount = PumpInsulin(param1 / 100.0),
                        type = detailedBolusInfo?.bolusType ?: bolusTypeFromCommand(datetime, param1 / 100.0),
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT BOLUS ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Bolus: ${param1 / 100.0}U "
                )
                if (!newRecord && detailedBolusInfo != null) {
                    // detailedInfo can be from another similar record. Reinsert
                    detailedBolusInfoStorage.add(detailedBolusInfo)
                }
                status = "BOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_BOLUS          -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncBolusWithPumpId(
                        timestamp = datetime,
                        amount = PumpInsulin(param1 / 100.0),
                        type = detailedBolusInfo?.bolusType,
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT DUAL_BOLUS ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Bolus: ${param1 / 100.0}U Duration: ${param2}min"
                )
                if (!newRecord && detailedBolusInfo != null) {
                    // detailedInfo can be from another similar record. Reinsert
                    detailedBolusInfoStorage.add(detailedBolusInfo)
                }
                status = "DUAL_BOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_EXTENDED_START -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncExtendedBolusWithPumpId(
                        timestamp = datetime,
                        rate = PumpRate(param1 / 100.0),
                        duration = T.mins(param2.toLong()).msecs(),
                        isEmulatingTB = false,
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT DUAL_EXTENDED_START ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U Duration: ${param2}min"
                )
                status = "DUAL_EXTENDED_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_EXTENDED_STOP  -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = datetime,
                        endPumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT DUAL_EXTENDED_STOP ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Delivered: ${param1 / 100.0}U RealDuration: ${param2}min"
                )
                status = "DUAL_EXTENDED_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.SUSPEND_ON          -> {
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT SUSPEND_ON ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime)"
                )
                status = "SUSPEND_ON " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.SUSPEND_OFF         -> {
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT SUSPEND_OFF ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime)"
                )
                status = "SUSPEND_OFF " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.REFILL              -> {
                if (preferences.get(DanaBooleanKey.LogInsulinChange)) {
                    val newRecord = runBlocking<Boolean?> {
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = datetime,
                            type = TE.Type.INSULIN_CHANGE,
                            pumpId = pumpId,
                            pumpType = danaPump.pumpType(),
                            pumpSerial = danaPump.serialNumber
                        )
                    } == true
                    aapsLogger.debug(
                        LTag.PUMPCOMM,
                        "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT REFILL ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U"
                    )
                }
                status = "REFILL " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.PRIME               -> {
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT PRIME ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U"
                )
                status = "PRIME " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.PROFILE_CHANGE      -> {
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT PROFILE_CHANGE ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) No: $param1 TotalBasal: ${param2 / 100.0}U"
                )
                status = "PROFILE_CHANGE " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.CARBS               -> {
                val newRecord = runBlocking<Boolean?> {
                    pumpSync.syncCarbsWithTimestamp(
                        timestamp = datetime,
                        amount = param1.toDouble(),
                        pumpId = pumpId,
                        pumpType = danaPump.pumpType(),
                        pumpSerial = danaPump.serialNumber
                    )
                } == true
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT CARBS ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Carbs: ${param1}g"
                )
                status = "CARBS " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.PRIME_CANNULA       -> {
                if (preferences.get(DanaBooleanKey.LogCannulaChange)) {
                    val newRecord = runBlocking<Boolean?> {
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = datetime,
                            type = TE.Type.CANNULA_CHANGE,
                            pumpId = pumpId,
                            pumpType = danaPump.pumpType(),
                            pumpSerial = danaPump.serialNumber
                        )
                    } == true
                    aapsLogger.debug(
                        LTag.PUMPCOMM,
                        "[$pumpId] ${if (newRecord) "**NEW** " else ""}EVENT PRIME_CANNULA($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U"
                    )
                }
                status = "PRIME_CANNULA " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.TIME_CHANGE         -> {
                val oldDateTime = intFromBuffMsbLsb(data, 7, 4) * 1000L
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT TIME_CHANGE($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Previous: ${dateUtil.dateAndTimeString(oldDateTime)}"
                )
                status = "TIME_CHANGE " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.STEP_BOLUS_COMMAND  -> {
                // Used through bolusTypeFromCommand() when the BOLUS record is synced
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT STEP_BOLUS_COMMAND ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Amount: ${param1 / 100.0}U Algorithm: ${param2 == 1}"
                )
                status = "STEP_BOLUS_COMMAND " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.BASAL_RATE_CHANGE   -> {
                val reason = param2 and BASAL_CHANGE_REASON_MASK
                val rate = if (param2 and BASAL_CHANGE_UNIT_PER_DAY != 0) "$param1 U/d" else "${param1 / 100.0} U/h"
                // Only logged for now. It is not known yet which reasons AAPS itself causes
                // (for example a profile switch may also write reason 7), so no action is taken.
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "[$pumpId] EVENT BASAL_RATE_CHANGE ($recordCode) ${dateUtil.dateAndTimeString(datetime)} ($datetime) Rate: $rate Reason: $reason ${basalChangeReasonName(reason)}"
                )
                status = "BASAL_RATE_CHANGE " + dateUtil.timeString(datetime)
            }
        }
        if (datetime > danaPump.lastEventTimeLoaded) danaPump.lastEventTimeLoaded = datetime
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.processinghistory) + ": " + status))
    }

    override val friendlyName: String = "APS_HISTORY_EVENTS"
}