package info.nightscout.androidaps.diaconn.packet

import android.content.Context
import android.content.pm.PackageManager
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.api.ApiResponse
import info.nightscout.androidaps.diaconn.api.DiaconnApiService
import info.nightscout.androidaps.diaconn.api.DiaconnLogUploader
import info.nightscout.androidaps.diaconn.api.PumpLog
import info.nightscout.androidaps.diaconn.api.PumpLogDto
import info.nightscout.androidaps.diaconn.common.RecordTypes
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecord
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecordDao
import info.nightscout.androidaps.diaconn.pumplog.LOG_ALARM_BATTERY
import info.nightscout.androidaps.diaconn.pumplog.LOG_ALARM_BLOCK
import info.nightscout.androidaps.diaconn.pumplog.LOG_ALARM_SHORTAGE
import info.nightscout.androidaps.diaconn.pumplog.LOG_CHANGE_INJECTOR_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_CHANGE_NEEDLE_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_CHANGE_TUBE_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECTION_1DAY
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECTION_1DAY_BASAL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECTION_1HOUR_BASAL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECTION_DUAL_NORMAL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_DUAL_FAIL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_DUAL_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_MEAL_FAIL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_MEAL_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_NORMAL_FAIL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_NORMAL_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_SQUARE_FAIL
import info.nightscout.androidaps.diaconn.pumplog.LOG_INJECT_SQUARE_SUCCESS
import info.nightscout.androidaps.diaconn.pumplog.LOG_RESET_SYS_V3
import info.nightscout.androidaps.diaconn.pumplog.LOG_SET_DUAL_INJECTION
import info.nightscout.androidaps.diaconn.pumplog.LOG_SET_SQUARE_INJECTION
import info.nightscout.androidaps.diaconn.pumplog.LOG_SUSPEND_RELEASE_V2
import info.nightscout.androidaps.diaconn.pumplog.LOG_SUSPEND_V2
import info.nightscout.androidaps.diaconn.pumplog.LOG_TB_START_V3
import info.nightscout.androidaps.diaconn.pumplog.LOG_TB_STOP_V3
import info.nightscout.androidaps.diaconn.pumplog.PumplogUtil
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.extensions.safeGetPackageInfo
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.TemporaryBasalStorage
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.T
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.time.DateUtils
import org.joda.time.DateTime
import retrofit2.Call
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject

/**
 * BigLogInquireResponsePacket
 */
class BigLogInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Inject lateinit var sp: SP
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Inject lateinit var diaconnLogUploader: DiaconnLogUploader
    @Inject lateinit var context: Context

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
        val result2 = getByteToInt(bufferData)  // 조회결과 1 byte
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        val logLength = getByteToInt(bufferData) // 로그의 갯수. 1byte

        // initialize
        val dailyMaxValInfo = mutableMapOf<String, MutableMap<String, Double>>()
        dailyMaxValInfo[""] = mutableMapOf()
        val pumpLogs: MutableList<PumpLog> = mutableListOf()

        // 15 byte 를 로그갯수만큼 돌기.
        for (i in 0 until logLength) {
            val wrappingCount = getByteToInt(bufferData) // 1byte
            val logNum = getShortToInt(bufferData)  // 2byte
            // log Data Parsing
            val logData = byteArrayOf(
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
            // process Log to DB
            val logDataToHexString = toNarrowHex(logData)
            val pumpLogKind: Byte = PumplogUtil.getKind(logDataToHexString)
            var status: String
            val diaconnG8HistoryRecord = DiaconnHistoryRecord(0)

            if (diaconnG8Pump.isPlatformUploadStarted) {
                // Diaconn Platform upload start
                aapsLogger.debug(LTag.PUMPCOMM, "make api upload parameter")
                val pumpLog = PumpLog(
                    pumplog_no = logNum.toLong(),
                    pumplog_wrapping_count = wrappingCount,
                    pumplog_data = logDataToHexString,
                    act_type = "1"
                )
                pumpLogs.add(pumpLog)
                continue

            } else {
                // APS Local history sync start
                diaconnG8Pump.apsWrappingCount = wrappingCount
                diaconnG8Pump.apslastLogNum = logNum

                when (pumpLogKind) {

                    LOG_INJECT_MEAL_SUCCESS.LOG_KIND     -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT MEAL_BOLUS ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Bolus: ${logItem.injectAmount / 100.0}U "
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "M" // meal bolus
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logmealsuccess)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        if (!newRecord && detailedBolusInfo != null) {
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        status = "MEAL_BOLUS_SUCCESS" + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_MEAL_FAIL.LOG_KIND        -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT MEAL_BOLUS ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Bolus: ${logItem.injectAmount / 100.0}U "
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = if ((logItem.injectAmount / 100.0) < 0) 0.0 else (logItem.injectAmount / 100.0)
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "M" // Meal bolus
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logmealfail)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        if (!newRecord && detailedBolusInfo != null) {
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        status = "MEAL_BOLUS_FAIL " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_NORMAL_SUCCESS.LOG_KIND   -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + pumpLogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U "
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "B" // bolus
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logsuccess)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        if (!newRecord && detailedBolusInfo != null) {
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        status = "BOLUS_SUCCESS" + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_NORMAL_FAIL.LOG_KIND      -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + pumpLogKind + ") " + dateUtil.dateAndTimeString(logDateTime) + " (" + logDateTime + ")" + " Bolus: " + logItem.injectAmount / 100.0 + "U "
                        )
                        // Diaconn History Process
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = if ((logItem.injectAmount / 100.0) < 0) 0.0 else (logItem.injectAmount / 100.0)
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "B" // bolus
                        diaconnG8HistoryRecord.stringValue = getReasonName(pumpLogKind, logItem.reason)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        if (!newRecord && detailedBolusInfo != null) {
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        status = "BOLUS_FAIL " + dateUtil.timeString(logDateTime)
                    }

                    LOG_SET_SQUARE_INJECTION.LOG_KIND    -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_START ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Amount: ${logItem.setAmount / 100.0}U Duration: ${logItem.getInjectTime() * 10}min"
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.setAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logsquarestart)
                        diaconnG8HistoryRecord.bolusType = "E" // Extended
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "EXTENDED_BOLUS_START " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_SQUARE_SUCCESS.LOG_KIND   -> {
                        val logItem = LOG_INJECT_SQUARE_SUCCESS.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logsquaresuccess)
                        diaconnG8HistoryRecord.bolusType = "E" // Extended
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "EXTENDED_BOLUS_END " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_SQUARE_FAIL.LOG_KIND      -> {
                        val logItem = LOG_INJECT_SQUARE_FAIL.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                            timestamp = logDateTime,
                            endPumpId = logDateTime,
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_STOP ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Delivered: ${logItem.injectAmount / 100.0}U RealDuration: ${logItem.getInjectTime()}min"
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.stringValue = getReasonName(pumpLogKind, logItem.reason)
                        diaconnG8HistoryRecord.bolusType = "E"
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "EXTENDED_BOLUS_FAIL " + dateUtil.timeString(logDateTime)
                    }

                    LOG_SET_DUAL_INJECTION.LOG_KIND      -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_START ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Amount: ${logItem.setSquareAmount / 100.0}U Duration: ${logItem.getInjectTime() * 10}min"
                        )
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.setSquareAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime() * 10 // (1~30) 1:10min 30:300min
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logdualsquarestart)
                        diaconnG8HistoryRecord.bolusType = "D" // Extended
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)

                        status = "DUAL_EXTENDED_START " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECTION_DUAL_NORMAL.LOG_KIND   -> {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT DUAL_BOLUS ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Bolus: ${logItem.injectAmount / 100.0}U Duration: ${logItem.getInjectTime()}min"
                        )

                        diaconnG8Pump.lastBolusAmount = logItem.injectAmount / 100.0
                        diaconnG8Pump.lastBolusTime = logDateTime

                        //Diaconn History
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "D" // bolus
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logdualnormalsuccess)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        if (!newRecord && detailedBolusInfo != null) {
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        status = "DUAL_BOLUS" + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_DUAL_SUCCESS.LOG_KIND     -> {
                        val logItem = LOG_INJECT_DUAL_SUCCESS.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectSquareAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "D"
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logdualsquaresuccess)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "DUAL_BOLUS_SQUARE_SUCCESS " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECT_DUAL_FAIL.LOG_KIND        -> {
                        val logItem = LOG_INJECT_DUAL_FAIL.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                            timestamp = logDateTime,
                            endPumpId = logDateTime,
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT EXTENDED_STOP ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Delivered: ${logItem.injectSquareAmount / 100.0}U RealDuration: ${logItem.getInjectTime()}min"
                        )

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.injectNormAmount / 100.0 + logItem.injectSquareAmount / 100.0
                        diaconnG8HistoryRecord.duration = logItem.getInjectTime()
                        diaconnG8HistoryRecord.bolusType = "D"
                        diaconnG8HistoryRecord.stringValue = getReasonName(pumpLogKind, logItem.reason)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "DUAL_BOLUS FAIL " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECTION_1HOUR_BASAL.LOG_KIND   -> {
                        val logItem = LOG_INJECTION_1HOUR_BASAL.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_BASALHOUR
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.beforeAmount / 100.0
                        diaconnG8HistoryRecord.stringValue = "TB before: ${logItem.beforeAmount / 100.0} / TB after: ${logItem.afterAmount / 100.0}"
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "1HOUR BASAL " + dateUtil.dateAndTimeString(logDateTime)
                    }

                    LOG_SUSPEND_V2.LOG_KIND              -> {
                        val logItem = LOG_SUSPEND_V2.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_SUSPEND
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_lgosuspend, logItem.getBasalPattern())
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "SUSPEND " + dateUtil.timeString(logDateTime)
                    }

                    LOG_SUSPEND_RELEASE_V2.LOG_KIND      -> {
                        val logItem = LOG_SUSPEND_RELEASE_V2.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_SUSPEND
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_lgorelease, logItem.getBasalPattern())
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "SUSPEND_RELEASE " + dateUtil.timeString(logDateTime)
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
                            aapsLogger.debug(
                                LTag.PUMPCOMM,
                                "${if (newRecord) "**NEW** " else ""}EVENT INSULIN_CHANGE($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Amount: ${logItem.remainAmount / 100.0}U"
                            )
                        }
                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_loginjectorprime, logItem.primeAmount / 100.0)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "INSULIN_CHANGE " + dateUtil.timeString(logDateTime)
                    }

                    LOG_CHANGE_TUBE_SUCCESS.LOG_KIND     -> {
                        val logItem = LOG_CHANGE_TUBE_SUCCESS.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        if (sp.getBoolean(R.string.key_diaconn_g8_logtubechange, true)) {
                            val newRecord = pumpSync.insertTherapyEventIfNewWithTimestamp(
                                timestamp = logDateTime,
                                type = DetailedBolusInfo.EventType.NOTE,
                                note = rh.gs(R.string.diaconn_g8_logtubeprime, logItem.primeAmount / 100.0),
                                pumpId = logDateTime,
                                pumpType = PumpType.DIACONN_G8,
                                pumpSerial = diaconnG8Pump.serialNo.toString()
                            )
                            aapsLogger.debug(
                                LTag.PUMPCOMM,
                                "${if (newRecord) "**NEW** " else ""}EVENT TUBE_CHANGE($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Amount: ${logItem.primeAmount / 100.0}U"
                            )
                        }


                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logtubeprime, logItem.primeAmount / 100.0)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "TUBE_CHANGE " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECTION_1DAY.LOG_KIND          -> { // Daily Bolus Log
                        val logItem = LOG_INJECTION_1DAY.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_DAILY
                        diaconnG8HistoryRecord.timestamp = DateTime(logDateTime).withTimeAtStartOfDay().millis
                        diaconnG8HistoryRecord.dailyBolus = logItem.extAmount / 100.0 + logItem.mealAmount / 100.0

                        val recordDateStr = "" + diaconnG8HistoryRecord.timestamp
                        var recordMap: MutableMap<String, Double> = mutableMapOf("dummy" to 0.0)

                        if (dailyMaxValInfo.containsKey(recordDateStr)) {
                            recordMap = dailyMaxValInfo[recordDateStr]!!
                        } else {
                            recordMap["bolus"] = 0.0
                            recordMap["basal"] = 0.0
                            dailyMaxValInfo[recordDateStr] = recordMap
                        }

                        if (diaconnG8HistoryRecord.dailyBolus > recordMap["bolus"]!!) {
                            recordMap["bolus"] = diaconnG8HistoryRecord.dailyBolus
                        } else {
                            diaconnG8HistoryRecord.dailyBolus = recordMap["bolus"]!!
                        }

                        if (recordMap["basal"]!! > 0.0) {
                            diaconnG8HistoryRecord.dailyBasal = recordMap["basal"]!!
                        }
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
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

                        status = "DAILY_BOLUS " + dateUtil.timeString(logDateTime)
                    }

                    LOG_INJECTION_1DAY_BASAL.LOG_KIND    -> { // Daily Basal Log
                        val logItem = LOG_INJECTION_1DAY_BASAL.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_DAILY
                        diaconnG8HistoryRecord.timestamp = DateTime(logDateTime).withTimeAtStartOfDay().millis
                        diaconnG8HistoryRecord.dailyBasal = logItem.amount / 100.0

                        val recordDateStr = "" + diaconnG8HistoryRecord.timestamp
                        var recordMap: MutableMap<String, Double> = mutableMapOf("dummy" to 0.0)

                        if (dailyMaxValInfo.containsKey(recordDateStr)) {
                            recordMap = dailyMaxValInfo[recordDateStr]!!
                        } else {
                            recordMap["bolus"] = 0.0
                            recordMap["basal"] = 0.0
                            dailyMaxValInfo[recordDateStr] = recordMap
                        }

                        if (diaconnG8HistoryRecord.dailyBasal > recordMap["basal"]!!) {
                            recordMap["basal"] = diaconnG8HistoryRecord.dailyBasal
                        } else {
                            diaconnG8HistoryRecord.dailyBasal = recordMap["basal"]!!
                        }

                        if (recordMap["bolus"]!! > 0.0) {
                            diaconnG8HistoryRecord.dailyBolus = recordMap["bolus"]!!
                        }
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
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

                        status = "DAILY_BASAL " + dateUtil.timeString(logDateTime)
                    }

                    LOG_CHANGE_NEEDLE_SUCCESS.LOG_KIND   -> {
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
                            aapsLogger.debug(
                                LTag.PUMPCOMM,
                                "${if (newRecord) "**NEW** " else ""}EVENT NEEDLE_CHANGE($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Amount: ${logItem.remainAmount / 100.0}U"
                            )
                        }

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.remainAmount / 100.0
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logneedleprime, logItem.primeAmount / 100.0)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "NEEDLE_CHANGE " + dateUtil.timeString(logDateTime)
                    }

                    LOG_TB_START_V3.LOG_KIND             -> {
                        val logItem = LOG_TB_START_V3.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        var absoluteRate = 0.0
                        if (logItem.getTbInjectRateRatio() >= 50000) {
                            val tempBasalPercent = logItem.getTbInjectRateRatio() - 50000
                            absoluteRate = pumpDesc.pumpType.determineCorrectBasalSize(diaconnG8Pump.baseAmount * (tempBasalPercent / 100.0))
                        }

                        if (logItem.getTbInjectRateRatio() in 1000..2500) {
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
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "${if (newRecord) "**NEW** " else ""}EVENT TEMP_START ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) Ratio: ${absoluteRate}U Duration: ${logItem.tbTime * 15}min"
                        )

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_TB
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.duration = logItem.tbTime * 15
                        diaconnG8HistoryRecord.value = absoluteRate
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logtempstart)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "TEMP_START " + dateUtil.timeString(logDateTime)
                    }

                    LOG_TB_STOP_V3.LOG_KIND              -> {
                        val logItem = LOG_TB_STOP_V3.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time
                        var absoluteRate = 0.0
                        if (logItem.getTbInjectRateRatio() >= 50000) {
                            val tempBasalPercent = logItem.getTbInjectRateRatio() - 50000
                            absoluteRate = diaconnG8Pump.baseAmount * (tempBasalPercent / 100.0)
                        }
                        if (logItem.getTbInjectRateRatio() in 1000..2500) {
                            absoluteRate = (logItem.getTbInjectRateRatio() - 1000) / 100.0
                        }

                        val newRecord = pumpSync.syncStopTemporaryBasalWithPumpId(
                            timestamp = logDateTime,
                            endPumpId = dateUtil.now(),
                            pumpType = PumpType.DIACONN_G8,
                            pumpSerial = diaconnG8Pump.serialNo.toString()
                        )
                        aapsLogger.debug(LTag.PUMPCOMM, "${if (newRecord) "**NEW** " else ""}EVENT TEMP_STOP ($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime)")


                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_TB
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = absoluteRate
                        diaconnG8HistoryRecord.stringValue = getReasonName(pumpLogKind, logItem.reason)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "TEMP_STOP " + dateUtil.timeString(logDateTime)
                    }

                    LOG_ALARM_BATTERY.LOG_KIND           -> { // BATTERY SHORTAGE ALARM
                        val logItem = LOG_ALARM_BATTERY.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")
                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logbatteryshorage)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "BATTERY_ALARM " + dateUtil.timeString(logDateTime)
                    }

                    LOG_ALARM_BLOCK.LOG_KIND             -> { // INJECTION BLOCKED ALARM
                        val logItem = LOG_ALARM_BLOCK.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.amount / 100.0
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_logalarmblock, getReasonName(pumpLogKind, logItem.reason))
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "BLOCK_ALARM " + dateUtil.timeString(logDateTime)
                    }

                    LOG_ALARM_SHORTAGE.LOG_KIND          -> { // INSULIN SHORTAGE ALARM
                        val logItem = LOG_ALARM_SHORTAGE.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.value = logItem.remain.toDouble()
                        diaconnG8HistoryRecord.stringValue = rh.gs(R.string.diaconn_g8_loginsulinshorage)
                        diaconnG8HistoryRecord.lognum = logNum
                        diaconnG8HistoryRecord.wrappingCount = wrappingCount
                        diaconnG8HistoryRecord.pumpUid = diaconnG8Pump.pumpUid
                        diaconnHistoryRecordDao.createOrUpdate(diaconnG8HistoryRecord)
                        status = "SHORT_AGE_ALARM " + dateUtil.timeString(logDateTime)
                    }

                    LOG_RESET_SYS_V3.LOG_KIND            -> {
                        val logItem = LOG_RESET_SYS_V3.parse(logDataToHexString)
                        aapsLogger.debug(LTag.PUMPCOMM, "$logItem ")

                        val logStartDate = DateUtils.parseDate(logItem.dttm, "yyyy-MM-dd HH:mm:ss")
                        val logDateTime = logStartDate.time

                        diaconnG8HistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                        diaconnG8HistoryRecord.timestamp = logDateTime
                        diaconnG8HistoryRecord.stringValue = getReasonName(pumpLogKind, logItem.reason)
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
                                aapsLogger.debug(
                                    LTag.PUMPCOMM,
                                    "${if (newRecord) "**NEW** " else ""}EVENT BATTERY_CHANGE($pumpLogKind) ${dateUtil.dateAndTimeString(logDateTime)} ($logDateTime) remainAmount: ${logItem.batteryRemain.toInt()}%"
                                )
                            }
                        }
                        status = "RESET " + dateUtil.timeString(logDateTime)
                    }

                    else                                 -> {
                        status = rh.gs(R.string.diaconn_g8_logsyncinprogress)
                        rxBus.send(EventPumpStatusChanged(status))
                        continue
                    }
                }
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.processinghistory) + ": " + status))
            }

        }

        // 플랫폼 동기화이면,
        if (diaconnG8Pump.isPlatformUploadStarted) {
            aapsLogger.debug(LTag.PUMPCOMM, "Diaconn api upload start!!")
            var appUid: String = sp.getString(R.string.key_diaconn_g8_appuid, "")
            if (appUid.isEmpty()) {
                appUid = UUID.randomUUID().toString()
                sp.putString(R.string.key_diaconn_g8_appuid, appUid)
            }
            //api send
            val retrofit = diaconnLogUploader.getRetrofitInstance()
            val api = retrofit?.create(DiaconnApiService::class.java)
            val pumpLogDto = PumpLogDto(
                app_uid = appUid,
                app_version = context.packageManager.safeGetPackageInfo(context.packageName, 0)
                    .versionName,
                pump_uid = diaconnG8Pump.pumpUid,
                pump_version = diaconnG8Pump.pumpVersion,
                incarnation_num = diaconnG8Pump.pumpIncarnationNum,
                pumplog_info = pumpLogs,
            )
            try {
                api?.uploadPumpLogs(pumpLogDto)?.enqueue(
                    object : retrofit2.Callback<ApiResponse> {
                        override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                            if (response.body()?.ok == true) {
                                aapsLogger.debug(LTag.PUMPCOMM, "logs upload Success")
                            }
                        }

                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            aapsLogger.error(LTag.PUMPCOMM, "api uploadPumpLogs failed")
                            diaconnG8Pump.isPumpLogUploadFailed = true
                            t.printStackTrace()
                        }
                    }
                )
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
            }
        }
    }

    override fun getFriendlyName(): String {
        return "BIG_LOG_INQUIRE_RESPONSE"
    }

    private fun getReasonName(logKind: Byte, reason: Byte): String {
        val logInjectNormalFail: Byte = 0x0B
        val logInjectSquareFail: Byte = 0x0E
        val logInjectDualFail: Byte = 0x11
        val logTBStopV3: Byte = 0x13
        val logResetSysV3: Byte = 0x01
        val logAlarmBlock: Byte = 0x29

        return when (logKind) {
            logInjectNormalFail, logInjectSquareFail, logInjectDualFail, logTBStopV3 -> failLog(reason)
            logResetSysV3                                                            -> resetLog(reason)
            logAlarmBlock                                                            -> blockLog(reason)
            else                                                                     -> ""
        }
    }

    private fun failLog(reason: Byte): String {
        return when (reason) {
            //1=Injection blockage, 2=Battery shortage, 3=Drug shortage, 4=User shutdown, 5=System reset, 6=Other, 7=Emergency shutdown
            0.toByte() -> rh.gs(R.string.diaconn_g8_reasoncomplete)
            1.toByte() -> rh.gs(R.string.diaconn_g8_reasoninjectonblock)
            2.toByte() -> rh.gs(R.string.diaconn_g8_reasonbatteryshortage)
            3.toByte() -> rh.gs(R.string.diaconn_g8_reasoninsulinshortage)
            4.toByte() -> rh.gs(R.string.diaconn_g8_reasonuserstop)
            5.toByte() -> rh.gs(R.string.diaconn_g8_reasonsystemreset)
            6.toByte() -> rh.gs(R.string.diaconn_g8_reasonother)
            7.toByte() -> rh.gs(R.string.diaconn_g8_reasonemergencystop)
            else       -> "No Reason"
        }
    }

    private fun resetLog(reason: Byte): String {
        return when (reason.toInt()) {
            1    -> rh.gs(R.string.diaconn_g8_resetfactoryreset)
            2    -> rh.gs(R.string.diaconn_g8_resetemergencyoff)
            3    -> rh.gs(R.string.diaconn_g8_resetbatteryreplacement)
            4    -> rh.gs(R.string.diaconn_g8_resetaftercalibration)
            5    -> rh.gs(R.string.diaconn_g8_resetpreshipment)
            9    -> rh.gs(R.string.diaconn_g8_resetunexpected)
            else -> ""
        }
    }

    private fun blockLog(reason: Byte): String {
        return when (reason.toInt()) {
            1    -> rh.gs(R.string.diacon_g8_blockbasal)
            2    -> rh.gs(R.string.diacon_g8_blockmealbolus)
            3    -> rh.gs(R.string.diacon_g8_blocknormalbolus)
            4    -> rh.gs(R.string.diacon_g8_blocksquarebolus)
            5    -> rh.gs(R.string.diacon_g8_blockdualbolus)
            6    -> rh.gs(R.string.diacon_g8_blockreplacetube)
            7    -> rh.gs(R.string.diacon_g8_blockreplaceneedle)
            8    -> rh.gs(R.string.diacon_g8_blockreplacesyringe)
            else -> ""
        }
    }
}