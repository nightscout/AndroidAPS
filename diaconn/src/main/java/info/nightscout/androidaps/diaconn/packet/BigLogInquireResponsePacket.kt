package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.common.RecordTypes
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecord
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecordDao
import info.nightscout.androidaps.diaconn.pumplog.*
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.TemporaryBasalStorage
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.apache.commons.lang3.time.DateUtils
import org.joda.time.DateTime
import javax.inject.Inject

/**
 * BigLogInquireResponsePacket
 */
class BigLogInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Inject lateinit var sp: SP
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    var result = 0// 조회결과
    private var pumpDesc = PumpDescription(PumpType.DIACONN_G8)
    init {
        msgType = 0xb2.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BigLogInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BigLogInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data) // 데이타 영역 15바이트 버퍼
        val result2 =  getByteToInt(bufferData)  // 조회결과 1 byte
        if(!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        val logLength = getByteToInt(bufferData) // 로그의 갯수. 1byte

        // initalize
        val dailyMaxvalInfo = mutableMapOf<String, MutableMap<String, Double>>()
        dailyMaxvalInfo[""] = mutableMapOf()

        // 15 byte를 로그갯수만큼 돌기.
        for(i in 0 until logLength) {
            val wrapingCount = getByteToInt(bufferData) // 1byte
            val logNum =  getShortToInt(bufferData)  // 2byte
            // log Data Parsing
            val logdata = byteArrayOf(
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData),
                PumplogUtil.getByte(bufferData)
            )

            diaconnG8Pump.apsWrappingCount = wrapingCount
            diaconnG8Pump.apslastLogNum  = logNum
            sp.putInt(resourceHelper.gs(R.string.apslastLogNum), logNum)
            sp.putInt(resourceHelper.gs(R.string.apsWrappingCount), wrapingCount)

            // process Log to DB
            val logDataToHexString = toNarrowHex(logdata)
            val pumplogKind: Byte = PumplogUtil.getKind(logDataToHexString)
            var status: String
            val diaconnG8HistoryRecord = DiaconnHistoryRecord(0)
            when(pumplogKind) {

                LOG_INJECT_MEAL_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_INJECT_MEAL_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(logDateTime, logItem.injectAmount / 100.0)
                    val newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.injectAmount / 100.0,
                        type = detailedBolusInfo?.bolusType,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT MEALBOLUS (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U ")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                    diaconnG8HistoryRecord.duration =  logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "M" // meal bolus
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logmealsuccess)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "MEALBOLUSSUCCESS" + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_MEAL_FAIL.LOG_KIND -> {
                    val logItem = LOG_INJECT_MEAL_FAIL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(logDateTime, logItem.injectAmount / 100.0)
                    val newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.injectAmount / 100.0,
                        type = detailedBolusInfo?.bolusType,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT MEALBOLUS (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U ")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = if ((logItem.injectAmount / 100.0) < 0) 0.0 else (logItem.injectAmount / 100.0)
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "M" // Meal bolus
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logmealfail)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "MEALBOLUSFAIL " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_NORMAL_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_INJECT_NORMAL_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(logDateTime, logItem.injectAmount / 100.0)
                    val newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.injectAmount / 100.0,
                        type = detailedBolusInfo?.bolusType,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U ")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "B" // bolus
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logsuccess)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "BOLUSSUCCESS" + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_NORMAL_FAIL.LOG_KIND -> {
                    val logItem = LOG_INJECT_NORMAL_FAIL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    // APS DB process
                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(logDateTime, logItem.injectAmount / 100.0)
                    val newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.injectAmount / 100.0,
                        type = detailedBolusInfo?.bolusType,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U ")
                    // Diaconn History Process
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = if ((logItem.injectAmount / 100.0) < 0) 0.0 else (logItem.injectAmount / 100.0)
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "B" // bolus
                    diaconnG8HistoryRecord.stringValue = getReasonName(pumplogKind, logItem.reason)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "BOLUSFAIL " + dateUtil.timeString(logDateTime)
                }

                LOG_SET_SQUARE_INJECTION.LOG_KIND -> {
                    val logItem = LOG_SET_SQUARE_INJECTION.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val newRecord = pumpSync.syncExtendedBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.setAmount / 100.0,
                        duration = T.mins((logItem.getInjectTime() * 10).toLong()).msecs(),
                        isEmulatingTB = false,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT EXTENDEDSTART (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Amount: " + logItem.setAmount / 100.0 + "U Duration: " + logItem.getInjectTime() * 10 + "min")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.setAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logsquarestart)
                    diaconnG8HistoryRecord.bolusType = "E" // Extended
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "EXTENDEDBOLUSSTART " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_SQUARE_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_INJECT_SQUARE_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logsquaresuccess)
                    diaconnG8HistoryRecord.bolusType = "E" // Extended
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "EXTENDEDBOLUSEND " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_SQUARE_FAIL.LOG_KIND -> {
                    val logItem = LOG_INJECT_SQUARE_FAIL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = logDateTime,
                        endPumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT EXTENDEDSTOP (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Delivered: " + logItem.injectAmount / 100.0 + "U RealDuration: " + logItem.getInjectTime() + "min")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.stringValue = getReasonName(pumplogKind, logItem.reason)
                    diaconnG8HistoryRecord.bolusType = "E"
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "EXTENDEDBOLUSFAIL " + dateUtil.timeString(logDateTime)
                }

                LOG_SET_DUAL_INJECTION.LOG_KIND -> {
                    val logItem = LOG_SET_DUAL_INJECTION.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    // dual square 처리.
                    val newRecord = pumpSync.syncExtendedBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.setSquareAmount / 100.0,
                        duration = T.mins((logItem.getInjectTime() * 10).toLong()).msecs(),
                        isEmulatingTB = false,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT EXTENDEDSTART (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Amount: " + logItem.setSquareAmount / 100.0 + "U Duration: " + logItem.getInjectTime() * 10 + "min")
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.setSquareAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime() * 10 // (1~30) 1:10min 30:300min
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logdualsquarestart)
                    diaconnG8HistoryRecord.bolusType = "D" // Extended
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)

                    status = "DUALEXTENTEDSTART " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECTION_DUAL_NORMAL.LOG_KIND -> {
                    val logItem = LOG_INJECTION_DUAL_NORMAL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(logDateTime, logItem.injectAmount / 100.0)
                    val newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = logDateTime,
                        amount = logItem.injectAmount / 100.0,
                        type = detailedBolusInfo?.bolusType,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT DUALBOLUS (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U Duration: " + logItem.getInjectTime() + "min")

                    diaconnG8Pump.lastBolusAmount = logItem.injectAmount / 100.0
                    diaconnG8Pump.lastBolusTime = logDateTime

                    //Diaconn History
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "D" // bolus
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logdualnormalsuccess)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "DUALBOLUS" + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_DUAL_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_INJECT_DUAL_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectSquareAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "D"
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logdualsquaresuccess)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "DUALBOLUS SQUARESUCCESS " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECT_DUAL_FAIL.LOG_KIND -> {
                    val logItem = LOG_INJECT_DUAL_FAIL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = logDateTime,
                        endPumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT EXTENDEDSTOP (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Delivered: " + logItem.injectSquareAmount / 100.0 + "U RealDuration: " + logItem.getInjectTime() + "min")

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.injectNormAmount / 100.0 + logItem.injectSquareAmount / 100.0
                    diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                    diaconnG8HistoryRecord.bolusType = "D"
                    diaconnG8HistoryRecord.stringValue = getReasonName(pumplogKind, logItem.reason)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "DUALBOLUS FAIL " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECTION_1HOUR_BASAL.LOG_KIND -> {
                    val logItem = LOG_INJECTION_1HOUR_BASAL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BASALHOUR
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.beforeAmount / 100.0
                    diaconnG8HistoryRecord.stringValue = "TB before: ${logItem.beforeAmount / 100.0} / TB after: ${logItem.afterAmount / 100.0}"
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "1HOUR BASAL " + dateUtil.dateAndTimeString(logDateTime)
                }

                LOG_SUSPEND_V2.LOG_KIND -> {
                    val logItem = LOG_SUSPEND_V2.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_SUSPEND
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_lgosuspend, logItem.getBasalPattern())
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "SUSPEND " + dateUtil.timeString(logDateTime)
                }

                LOG_SUSPEND_RELEASE_V2.LOG_KIND -> {
                    val logItem = LOG_SUSPEND_RELEASE_V2.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_SUSPEND
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_lgorelease, logItem.getBasalPattern())
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "SUSPENDRELEASE " + dateUtil.timeString(logDateTime)
                }

                LOG_CHANGE_INJECTOR_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_CHANGE_INJECTOR_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    if (sp.getBoolean(R.string.key_diaconn_g8_loginsulinchange, true)) {
                        val newRecord = pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = logDateTime,
                            type = DetailedBolusInfo.EventType.INSULIN_CHANGE,
                            pumpId = logDateTime,
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT INSULINCHANGE(" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Amount: " + logItem.remainAmount / 100.0 + "U")
                    }
                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_loginjectorprime, logItem.primeAmount / 100.0)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "INSULINCHANGE " + dateUtil.timeString(logDateTime)
                }

                LOG_CHANGE_TUBE_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_CHANGE_TUBE_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    if (sp.getBoolean(R.string.key_diaconn_g8_logtubechange, true)) {
                        val newRecord = pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = logDateTime,
                            type = DetailedBolusInfo.EventType.NOTE,
                            note = resourceHelper.gs(R.string.diaconn_g8_logtubeprime, logItem.primeAmount / 100.0),
                            pumpId = logDateTime,
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT TUBECHANGE(" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Amount: " + logItem.primeAmount / 100.0 + "U")
                    }


                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logtubeprime, logItem.primeAmount / 100.0)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "TUBECHANGE " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECTION_1DAY.LOG_KIND -> { // Daily Bolus Log
                    val logItem = LOG_INJECTION_1DAY.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_DAILY
                    diaconnG8HistoryRecord.timestamp = DateTime(logDateTime).withTimeAtStartOfDay().millis
                    diaconnG8HistoryRecord.dailyBolus = logItem.extAmount / 100.0 + logItem.mealAmount / 100.0

                    val recordDateStr = "" + diaconnG8HistoryRecord.timestamp
                    var recordMap: MutableMap<String, Double> = mutableMapOf("dummy" to 0.0)

                    if (dailyMaxvalInfo.containsKey(recordDateStr)) {
                        recordMap = dailyMaxvalInfo[recordDateStr]!!
                    } else {
                        recordMap["bolus"] = 0.0
                        recordMap["basal"] = 0.0
                        dailyMaxvalInfo[recordDateStr] = recordMap
                    }

                    if (diaconnG8HistoryRecord.dailyBolus > recordMap["bolus"]!!) {
                        recordMap["bolus"] = diaconnG8HistoryRecord.dailyBolus
                    } else {
                        diaconnG8HistoryRecord.dailyBolus = recordMap["bolus"]!!
                    }

                    if (recordMap["basal"]!! > 0.0) {
                        diaconnG8HistoryRecord.dailyBasal = recordMap["basal"]!!
                    }

                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)

                    //If it is a TDD, store it for stats also.
                    pumpSync.createOrUpdateTotalDailyDose(
                        timestamp = diaconnG8HistoryRecord.timestamp,
                        bolusAmount = diaconnG8HistoryRecord.dailyBolus,
                        basalAmount = diaconnG8HistoryRecord.dailyBasal,
                        totalAmount = 0.0,
                        pumpId = null,
                        pumpType = PumpType.DIACONN_G8,
                        diaconnG8Pump.serialNo.toString()
                    )

                    status = "DAILYBOLUS " + dateUtil.timeString(logDateTime)
                }

                LOG_INJECTION_1DAY_BASAL.LOG_KIND -> { // Daily Basal Log
                    val logItem = LOG_INJECTION_1DAY_BASAL.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_DAILY
                    diaconnG8HistoryRecord.timestamp = DateTime(logDateTime).withTimeAtStartOfDay().millis
                    diaconnG8HistoryRecord.dailyBasal = logItem.amount / 100.0

                    val recordDateStr = "" + diaconnG8HistoryRecord.timestamp
                    var recordMap: MutableMap<String, Double> = mutableMapOf("dummy" to 0.0)

                    if (dailyMaxvalInfo.containsKey(recordDateStr)) {
                        recordMap = dailyMaxvalInfo[recordDateStr]!!
                    } else {
                        recordMap["bolus"] = 0.0
                        recordMap["basal"] = 0.0
                        dailyMaxvalInfo[recordDateStr] = recordMap
                    }

                    if (diaconnG8HistoryRecord.dailyBasal > recordMap["basal"]!!) {
                        recordMap["basal"] = diaconnG8HistoryRecord.dailyBasal
                    } else {
                        diaconnG8HistoryRecord.dailyBasal = recordMap["basal"]!!
                    }

                    if (recordMap["bolus"]!! > 0.0) {
                        diaconnG8HistoryRecord.dailyBolus = recordMap["bolus"]!!
                    }

                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)

                    //If it is a TDD, store it for stats also.
                    // pumpSync.createOrUpdateTotalDailyDose(
                    //     timestamp = diaconnG8HistoryRecord.timestamp,
                    //     bolusAmount = diaconnG8HistoryRecord.dailyBolus,
                    //     basalAmount = diaconnG8HistoryRecord.dailyBasal,
                    //     totalAmount = 0.0,
                    //     pumpId = null,
                    //     pumpType = PumpType.DIACONN_G8,
                    //     diaconnG8Pump.serialNo.toString()
                    // )


                    status = "DAILYBASAL " + dateUtil.timeString(logDateTime)
                }

                LOG_CHANGE_NEEDLE_SUCCESS.LOG_KIND -> {
                    val logItem = LOG_CHANGE_NEEDLE_SUCCESS.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    if (sp.getBoolean(R.string.key_diaconn_g8_logneedlechange, true)) {
                        val newRecord = pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = logDateTime,
                            type = DetailedBolusInfo.EventType.CANNULA_CHANGE,
                            pumpId = logDateTime,
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(LTag.PUMPCOMM,  (if (newRecord) "**NEW** " else "") + "EVENT NEEDLECHANGE(" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Amount: " + logItem.remainAmount / 100.0 + "U")
                    }

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logneedleprime, logItem.primeAmount / 100.0)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "NEEDLECHANGE " + dateUtil.timeString(logDateTime)
                }

                LOG_TB_START_V3.LOG_KIND -> {
                    val logItem = LOG_TB_START_V3.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    var absoluteRate = 0.0
                    if (logItem.getTbInjectRateRatio() >= 50000) {
                        val tempBasalPercent = logItem.getTbInjectRateRatio() - 50000
                        absoluteRate = pumpDesc.pumpType.determineCorrectBasalSize(diaconnG8Pump.baseAmount * (tempBasalPercent / 100.0))
                    }

                    if (logItem.getTbInjectRateRatio() in 1000..1600) {
                        absoluteRate = (logItem.getTbInjectRateRatio() - 1000) / 100.0
                    }

                    val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(logDateTime, absoluteRate)
                    val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = logDateTime,
                        rate = absoluteRate,
                        duration = T.mins((logItem.tbTime * 15).toLong()).msecs(),
                        isAbsolute = true,
                        type = temporaryBasalInfo?.type,
                        pumpId = logDateTime,
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                   aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT TEMPSTART (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Ratio: " + absoluteRate + "U Duration: " + logItem.tbTime * 15 + "min")

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_TB
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.duration = logItem.tbTime * 15
                    diaconnG8HistoryRecord.value = absoluteRate
                    diaconnG8HistoryRecord.stringValue =  resourceHelper.gs(R.string.diaconn_g8_logtempstart)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "TEMPSTART " + dateUtil.timeString(logDateTime)
                }

                LOG_TB_STOP_V3.LOG_KIND -> {
                    val logItem = LOG_TB_STOP_V3.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time
                    var absoluteRate = 0.0
                    if (logItem.getTbInjectRateRatio() >= 50000) {
                        val tempBasalPercent = logItem.getTbInjectRateRatio() - 50000
                        absoluteRate = diaconnG8Pump.baseAmount * (tempBasalPercent / 100.0)
                    }
                    if (logItem.getTbInjectRateRatio() in 1000..1600) {
                        absoluteRate = (logItem.getTbInjectRateRatio() - 1000) / 100.0
                    }

                    val newRecord = pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = logDateTime,
                        endPumpId = dateUtil.now(),
                        pumpType = PumpType.DIACONN_G8,
                        pumpSerial = diaconnG8Pump.serialNo.toString())
                    aapsLogger.debug(LTag.PUMPCOMM, (if (newRecord) "**NEW** " else "") + "EVENT TEMPSTOP (" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")")


                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_TB
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = absoluteRate
                    diaconnG8HistoryRecord.stringValue = getReasonName(pumplogKind, logItem.reason)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "TEMPSTOP " + dateUtil.timeString(logDateTime)
                }

                LOG_ALARM_BATTERY.LOG_KIND -> { // BATTERY SHORTAGE ALARM
                    val logItem = LOG_ALARM_BATTERY.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logbatteryshorage)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "BATTERYALARM " + dateUtil.timeString(logDateTime)
                }

                LOG_ALARM_BLOCK.LOG_KIND -> { // INJECTION BLOCKED ALARM
                    val logItem = LOG_ALARM_BLOCK.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.amount / 100.0
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_logalarmblock, getReasonName(pumplogKind, logItem.reason))
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "BLOCKALARM " + dateUtil.timeString(logDateTime)
                }

                LOG_ALARM_SHORTAGE.LOG_KIND -> { // INSULIN SHORTAGE ALARM
                    val logItem = LOG_ALARM_SHORTAGE.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.value = logItem.remain.toDouble()
                    diaconnG8HistoryRecord.stringValue = resourceHelper.gs(R.string.diaconn_g8_loginsulinshorage)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    status = "SHORTAGEALARM " + dateUtil.timeString(logDateTime)
                }

                LOG_RESET_SYS_V3.LOG_KIND -> {
                    val logItem = LOG_RESET_SYS_V3.parse(logDataToHexString)
                    aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                    val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                    val logDateTime = logStartDate.time

                    diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                    diaconnG8HistoryRecord.timestamp = logDateTime
                    diaconnG8HistoryRecord.stringValue = getReasonName(pumplogKind, logItem.reason)
                    diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                    if (logItem.reason == 3.toByte()) {
                        if (sp.getBoolean(R.string.key_diaconn_g8_logbatterychange, true)) {
                            val newRecord = pumpSync.insertTherapyEventIfNewWithTimestamp(
                                timestamp = logDateTime,
                                type = DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE,
                                pumpId = logDateTime,
                                pumpType = PumpType.DIACONN_G8,
                                pumpSerial = diaconnG8Pump.serialNo.toString()
                            )
                            aapsLogger.debug(LTag.PUMPCOMM,  (if (newRecord) "**NEW** " else "") + "EVENT BATTERYCHANGE(" + pumplogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " remainAmount: " + logItem.batteryRemain.toInt() + "%")
                        }
                    }
                    status = "RESET " + dateUtil.timeString(logDateTime)
                }

                else       -> {
                    status = resourceHelper.gs(R.string.diaconn_g8_logsyncinprogress)
                    rxBus.send(EventPumpStatusChanged(status))
                    continue
                }
            }
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.processinghistory) + ": " + status))
        }
    }

    override fun getFriendlyName(): String {
        return "BIG_LOG_INQUIRE_RESPONSE"
    }

    private fun getReasonName(logKind: Byte, reason: Byte): String{
        val logInjectNormalFail: Byte = 0x0B
        val logInjectSquareFail: Byte = 0x0E
        val logInjectDualFail: Byte = 0x11
        val logTBStopV3: Byte = 0x13
        val logResetSysV3: Byte = 0x01
        val logALarmBlock: Byte = 0x29

        return when (logKind) {
            logInjectNormalFail, logInjectSquareFail, logInjectDualFail, logTBStopV3 -> failLog(reason)
            logResetSysV3 -> resetLog(reason)
            logALarmBlock -> blockLog(reason)
            else    -> ""
        }
    }

    private fun failLog(reason: Byte): String {
        return when (reason) {
            //1=Injection blockage, 2=Battery shortage, 3=Drug shortage, 4=User shutdown, 5=System reset, 6=Other, 7=Emergency shutdown
            0.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasoncomplete)
            1.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasoninjectonblock)
            2.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasonbatteryshortage)
            3.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasoninsulinshortage)
            4.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasonuserstop)
            5.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasonsystemreset)
            6.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasonother)
            7.toByte() -> resourceHelper.gs(R.string.diaconn_g8_reasonemergencystop)
            else       -> "No Reason"
        }
    }

    private fun resetLog(reason: Byte): String {
        return when (reason.toInt()) {
            1 -> resourceHelper.gs(R.string.diaconn_g8_resetfactoryreset)
            2 -> resourceHelper.gs(R.string.diaconn_g8_resetemergencyoff)
            3 -> resourceHelper.gs(R.string.diaconn_g8_resetbatteryreplacement)
            4 -> resourceHelper.gs(R.string.diaconn_g8_resetaftercalibration)
            5 -> resourceHelper.gs(R.string.diaconn_g8_resetpreshipment)
            9 -> resourceHelper.gs(R.string.diaconn_g8_resetunexpected)
            else -> ""
        }
    }

    private fun blockLog(reason: Byte): String {
        return when (reason.toInt()) {
            1 -> resourceHelper.gs(R.string.diacon_g8_blockbasal)
            2 -> resourceHelper.gs(R.string.diacon_g8_blockmealbolus)
            3 -> resourceHelper.gs(R.string.diacon_g8_blocknormalbolus)
            4 -> resourceHelper.gs(R.string.diacon_g8_blocksquarebolus)
            5 -> resourceHelper.gs(R.string.diacon_g8_blockdualbolus)
            6 -> resourceHelper.gs(R.string.diacon_g8_blockreplacetube)
            7 -> resourceHelper.gs(R.string.diacon_g8_blockreplaceneedle)
            8 -> resourceHelper.gs(R.string.diacon_g8_blockreplacesyringe)
            else -> ""
        }
    }

}