package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.dialogs.FillDialog
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

open class DanaRS_Packet_APS_History_Events(
    injector: HasAndroidInjector,
    private var from: Long
) : DanaRS_Packet(injector) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var danaRPump: DanaRPump
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var sp: SP

    private var year = 0
    private var month = 0
    private var day = 0
    private var hour = 0
    private var min = 0
    private var sec = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS
        val cal = GregorianCalendar()
        if (from > DateUtil.now()) {
            aapsLogger.debug(LTag.PUMPCOMM, "Asked to load from the future")
            from = 0
        }
        if (from != 0L) cal.timeInMillis = from else cal[2000, 0, 1, 0, 0] = 0
        year = cal[Calendar.YEAR] - 1900 - 100
        month = cal[Calendar.MONTH] + 1
        day = cal[Calendar.DAY_OF_MONTH]
        hour = cal[Calendar.HOUR_OF_DAY]
        min = cal[Calendar.MINUTE]
        sec = cal[Calendar.SECOND]
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + DateUtil.dateAndTimeString(cal.timeInMillis))
        danaRPump.historyDoneReceived = false
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(6)
        request[0] = (year and 0xff).toByte()
        request[1] = (month and 0xff).toByte()
        request[2] = (day and 0xff).toByte()
        request[3] = (hour and 0xff).toByte()
        request[4] = (min and 0xff).toByte()
        request[5] = (sec and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val recordCode = intFromBuff(data, 0, 1).toByte()
        // Last record
        if (recordCode == 0xFF.toByte()) {
            danaRPump.historyDoneReceived = true
            aapsLogger.debug(LTag.PUMPCOMM, "Last record received")
            return
        }
        val datetime = dateTimeSecFromBuff(data, 1) // 6 bytes
        val param1 = (intFromBuff(data, 7, 1) shl 8 and 0xFF00) + (intFromBuff(data, 8, 1) and 0xFF)
        val param2 = (intFromBuff(data, 9, 1) shl 8 and 0xFF00) + (intFromBuff(data, 10, 1) and 0xFF)
        val temporaryBasal = TemporaryBasal(injector).date(datetime).source(Source.PUMP).pumpId(datetime)
        val extendedBolus = ExtendedBolus(injector).date(datetime).source(Source.PUMP).pumpId(datetime)
        val status: String
        when (recordCode.toInt()) {
            DanaRPump.TEMPSTART         -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT TEMPSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Ratio: " + param1 + "% Duration: " + param2 + "min")
                temporaryBasal.percentRate = param1
                temporaryBasal.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryTempBasal(temporaryBasal)
                status = "TEMPSTART " + DateUtil.timeString(datetime)
            }

            DanaRPump.TEMPSTOP          -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT TEMPSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime))
                activePlugin.activeTreatments.addToHistoryTempBasal(temporaryBasal)
                status = "TEMPSTOP " + DateUtil.timeString(datetime)
            }

            DanaRPump.EXTENDEDSTART     -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT EXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                extendedBolus.insulin = param1 / 100.0
                extendedBolus.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "EXTENDEDSTART " + DateUtil.timeString(datetime)
            }

            DanaRPump.EXTENDEDSTOP      -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT EXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min")
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "EXTENDEDSTOP " + DateUtil.timeString(datetime)
            }

            DanaRPump.BOLUS             -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                    ?: DetailedBolusInfo()
                detailedBolusInfo.date = datetime
                detailedBolusInfo.source = Source.PUMP
                detailedBolusInfo.pumpId = datetime
                detailedBolusInfo.insulin = param1 / 100.0
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                status = "BOLUS " + DateUtil.timeString(datetime)
            }

            DanaRPump.DUALBOLUS         -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                    ?: DetailedBolusInfo()
                detailedBolusInfo.date = datetime
                detailedBolusInfo.source = Source.PUMP
                detailedBolusInfo.pumpId = datetime
                detailedBolusInfo.insulin = param1 / 100.0
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT DUALBOLUS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                status = "DUALBOLUS " + DateUtil.timeString(datetime)
            }

            DanaRPump.DUALEXTENDEDSTART -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT DUALEXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                extendedBolus.insulin = param1 / 100.0
                extendedBolus.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "DUALEXTENDEDSTART " + DateUtil.timeString(datetime)
            }

            DanaRPump.DUALEXTENDEDSTOP  -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min")
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "DUALEXTENDEDSTOP " + DateUtil.timeString(datetime)
            }

            DanaRPump.SUSPENDON         -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT SUSPENDON (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")")
                status = "SUSPENDON " + DateUtil.timeString(datetime)
            }

            DanaRPump.SUSPENDOFF        -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT SUSPENDOFF (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")")
                status = "SUSPENDOFF " + DateUtil.timeString(datetime)
            }

            DanaRPump.REFILL            -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT REFILL (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                if (sp.getBoolean(R.string.key_rs_loginsulinchange, true))
                    FillDialog.generateCareportalEvent(CareportalEvent.INSULINCHANGE, datetime, resourceHelper.gs(R.string.danarspump), resourceHelper, sp, injector)
                status = "REFILL " + DateUtil.timeString(datetime)
            }

            DanaRPump.PRIME             -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PRIME (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                if (sp.getBoolean(R.string.key_rs_logcanulachange, true))
                    FillDialog.generateCareportalEvent(CareportalEvent.SITECHANGE, datetime, resourceHelper.gs(R.string.danarspump), resourceHelper, sp, injector)
                status = "PRIME " + DateUtil.timeString(datetime)
            }

            DanaRPump.PROFILECHANGE     -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PROFILECHANGE (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " No: " + param1 + " CurrentRate: " + param2 / 100.0 + "U/h")
                status = "PROFILECHANGE " + DateUtil.timeString(datetime)
            }

            DanaRPump.CARBS             -> {
                val emptyCarbsInfo = DetailedBolusInfo()
                emptyCarbsInfo.carbs = param1.toDouble()
                emptyCarbsInfo.date = datetime
                emptyCarbsInfo.source = Source.PUMP
                emptyCarbsInfo.pumpId = datetime
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(emptyCarbsInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT CARBS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Carbs: " + param1 + "g")
                status = "CARBS " + DateUtil.timeString(datetime)
            }

            DanaRPump.PRIMECANNULA      -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PRIMECANNULA(" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                status = "PRIMECANNULA " + DateUtil.timeString(datetime)
            }

            else                        -> {
                aapsLogger.debug(LTag.PUMPCOMM, "Event: " + recordCode + " " + DateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Param1: " + param1 + " Param2: " + param2)
                status = "UNKNOWN " + DateUtil.timeString(datetime)
            }
        }
        if (datetime > danaRPump.lastEventTimeLoaded) danaRPump.lastEventTimeLoaded = datetime
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.processinghistory) + ": " + status))
    }

    override fun getFriendlyName(): String {
        return "APS_HISTORY_EVENTS"
    }
}