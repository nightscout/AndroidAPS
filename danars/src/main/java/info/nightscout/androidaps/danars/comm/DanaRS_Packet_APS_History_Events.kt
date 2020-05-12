package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
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
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var sp: SP
    @Inject lateinit var nsUpload: NSUpload

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
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(cal.timeInMillis))
        danaPump.historyDoneReceived = false
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
            danaPump.historyDoneReceived = true
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
            DanaPump.TEMPSTART         -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT TEMPSTART (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Ratio: " + param1 + "% Duration: " + param2 + "min")
                temporaryBasal.percentRate = param1
                temporaryBasal.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryTempBasal(temporaryBasal)
                status = "TEMPSTART " + dateUtil.timeString(datetime)
            }

            DanaPump.TEMPSTOP          -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT TEMPSTOP (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime))
                activePlugin.activeTreatments.addToHistoryTempBasal(temporaryBasal)
                status = "TEMPSTOP " + dateUtil.timeString(datetime)
            }

            DanaPump.EXTENDEDSTART     -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT EXTENDEDSTART (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                extendedBolus.insulin = param1 / 100.0
                extendedBolus.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "EXTENDEDSTART " + dateUtil.timeString(datetime)
            }

            DanaPump.EXTENDEDSTOP      -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT EXTENDEDSTOP (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min")
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "EXTENDEDSTOP " + dateUtil.timeString(datetime)
            }

            DanaPump.BOLUS             -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                    ?: DetailedBolusInfo()
                detailedBolusInfo.date = datetime
                detailedBolusInfo.source = Source.PUMP
                detailedBolusInfo.pumpId = datetime
                detailedBolusInfo.insulin = param1 / 100.0
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                status = "BOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.DUALBOLUS         -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                    ?: DetailedBolusInfo()
                detailedBolusInfo.date = datetime
                detailedBolusInfo.source = Source.PUMP
                detailedBolusInfo.pumpId = datetime
                detailedBolusInfo.insulin = param1 / 100.0
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT DUALBOLUS (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                status = "DUALBOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.DUALEXTENDEDSTART -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT DUALEXTENDEDSTART (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min")
                extendedBolus.insulin = param1 / 100.0
                extendedBolus.durationInMinutes = param2
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "DUALEXTENDEDSTART " + dateUtil.timeString(datetime)
            }

            DanaPump.DUALEXTENDEDSTOP  -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min")
                activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                status = "DUALEXTENDEDSTOP " + dateUtil.timeString(datetime)
            }

            DanaPump.SUSPENDON         -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT SUSPENDON (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")")
                status = "SUSPENDON " + dateUtil.timeString(datetime)
            }

            DanaPump.SUSPENDOFF        -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT SUSPENDOFF (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")")
                status = "SUSPENDOFF " + dateUtil.timeString(datetime)
            }

            DanaPump.REFILL            -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT REFILL (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                if (sp.getBoolean(R.string.key_rs_loginsulinchange, true))
                    nsUpload.generateCareportalEvent(CareportalEvent.INSULINCHANGE, datetime, resourceHelper.gs(R.string.danarspump))
                status = "REFILL " + dateUtil.timeString(datetime)
            }

            DanaPump.PRIME             -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PRIME (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                if (sp.getBoolean(R.string.key_rs_logcanulachange, true))
                    nsUpload.generateCareportalEvent(CareportalEvent.SITECHANGE, datetime, resourceHelper.gs(R.string.danarspump))
                status = "PRIME " + dateUtil.timeString(datetime)
            }

            DanaPump.PROFILECHANGE     -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PROFILECHANGE (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " No: " + param1 + " CurrentRate: " + param2 / 100.0 + "U/h")
                status = "PROFILECHANGE " + dateUtil.timeString(datetime)
            }

            DanaPump.CARBS             -> {
                val emptyCarbsInfo = DetailedBolusInfo()
                emptyCarbsInfo.carbs = param1.toDouble()
                emptyCarbsInfo.date = datetime
                emptyCarbsInfo.source = Source.PUMP
                emptyCarbsInfo.pumpId = datetime
                val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(emptyCarbsInfo, false)
                aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT CARBS (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Carbs: " + param1 + "g")
                status = "CARBS " + dateUtil.timeString(datetime)
            }

            DanaPump.PRIMECANNULA      -> {
                aapsLogger.debug(LTag.PUMPCOMM, "EVENT PRIMECANNULA(" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U")
                status = "PRIMECANNULA " + dateUtil.timeString(datetime)
            }

            else                                                       -> {
                aapsLogger.debug(LTag.PUMPCOMM, "Event: " + recordCode + " " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Param1: " + param1 + " Param2: " + param2)
                status = "UNKNOWN " + dateUtil.timeString(datetime)
            }
        }
        if (datetime > danaPump.lastEventTimeLoaded) danaPump.lastEventTimeLoaded = datetime
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.processinghistory) + ": " + status))
    }

    override fun getFriendlyName(): String {
        return "APS_HISTORY_EVENTS"
    }
}