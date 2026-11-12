package app.aaps.pump.diaconn.service

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSuspendSync
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.workflow.MetroService
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.DiaconnTempBasalSync
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.api.DiaconnApiService
import app.aaps.pump.diaconn.api.DiaconnLogUploader
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.pump.diaconn.events.EventDiaconnG8NewStatus
import app.aaps.pump.diaconn.keys.DiaconnBooleanKey
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import app.aaps.pump.diaconn.keys.DiaconnIntNonKey
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.pump.diaconn.packet.AppConfirmSettingPacket
import app.aaps.pump.diaconn.packet.BasalLimitInquirePacket
import app.aaps.pump.diaconn.packet.BasalSettingPacket
import app.aaps.pump.diaconn.packet.BigAPSMainInfoInquirePacket
import app.aaps.pump.diaconn.packet.BigLogInquirePacket
import app.aaps.pump.diaconn.packet.BigMainInfoInquirePacket
import app.aaps.pump.diaconn.packet.BolusSpeedInquirePacket
import app.aaps.pump.diaconn.packet.BolusSpeedSettingPacket
import app.aaps.pump.diaconn.packet.DiaconnG8Packet
import app.aaps.pump.diaconn.packet.DisplayTimeInquirePacket
import app.aaps.pump.diaconn.packet.DisplayTimeoutSettingPacket
import app.aaps.pump.diaconn.packet.IncarnationInquirePacket
import app.aaps.pump.diaconn.packet.InjectionBasalSettingPacket
import app.aaps.pump.diaconn.packet.InjectionCancelSettingPacket
import app.aaps.pump.diaconn.packet.InjectionExtendedBolusSettingPacket
import app.aaps.pump.diaconn.packet.InjectionSnackInquirePacket
import app.aaps.pump.diaconn.packet.InjectionSnackSettingPacket
import app.aaps.pump.diaconn.packet.LanguageInquirePacket
import app.aaps.pump.diaconn.packet.LanguageSettingPacket
import app.aaps.pump.diaconn.packet.LogStatusInquirePacket
import app.aaps.pump.diaconn.packet.SerialNumInquirePacket
import app.aaps.pump.diaconn.packet.SnackLimitInquirePacket
import app.aaps.pump.diaconn.packet.SoundInquirePacket
import app.aaps.pump.diaconn.packet.SoundSettingPacket
import app.aaps.pump.diaconn.packet.TempBasalInquirePacket
import app.aaps.pump.diaconn.packet.TempBasalSettingPacket
import app.aaps.pump.diaconn.packet.TimeInquirePacket
import app.aaps.pump.diaconn.packet.TimeSettingPacket
import app.aaps.pump.diaconn.pumplog.PumpLogUtil
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Android service that runs the Diaconn G8 commands over [BLECommonService]: status and history read, bolus, temp
 * basal, extended bolus, basal profile and user settings.
 */
class DiaconnG8Service : MetroService() {

    @Inject lateinit var injector: MetroMemberInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var diaconnG8Plugin: DiaconnG8Plugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var bleCommonService: BLECommonService
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var diaconnLogUploader: DiaconnLogUploader
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var pumpEnactResultProvider: () -> PumpEnactResult
    @Inject lateinit var ch: ConcentrationHelper
    @Inject lateinit var bolusProgressData: BolusProgressData

    private var scope: CoroutineScope? = null
    private val mBinder: IBinder = LocalBinder()
    private var lastApproachingDailyLimit: Long = 0

    private var updateBolusSpeedInPump = false

    override fun onCreate() {
        super.onCreate()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        // Same scope as the preference observer below: IO, like the io scheduler used before, and
        // cancelled in onDestroy like the CompositeDisposable was cleared. UNDISPATCHED because
        // RxBus has no replay, so a scheduled collector could miss an exit sent before it starts.
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { stopSelf() }
        preferences.observe(DiaconnIntKey.BolusSpeed).drop(1).onEach {
            diaconnG8Pump.bolusSpeed = preferences.get(DiaconnIntKey.BolusSpeed)
            diaconnG8Pump.speed = preferences.get(DiaconnIntKey.BolusSpeed)
            updateBolusSpeedInPump = true
        }.launchIn(newScope)
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: DiaconnG8Service
            get() = this@DiaconnG8Service
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    val isConnected: Boolean
        get() = bleCommonService.isConnected

    val isConnecting: Boolean
        get() = bleCommonService.isConnecting

    fun connect(from: String, address: String): Boolean {
        return bleCommonService.connect(from, address)
    }

    fun stopConnecting() {
        bleCommonService.stopConnecting()
    }

    fun disconnect(from: String) {
        bleCommonService.disconnect(from)
    }

    fun sendMessage(message: DiaconnG8Packet, waitMillis: Long) {
        bleCommonService.sendMessage(message, waitMillis)
    }

    private fun sendMessage(message: DiaconnG8Packet) {
        bleCommonService.sendMessage(message, 5000)
    }

    /** Reads the pump status and settings, checks the pump clock and sets it when needed, then reads the pump history. */
    suspend fun readPumpStatus() {
        try {
            val pump = activePlugin.activePump
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
            sendMessage(SerialNumInquirePacket(injector))

            val pumpFirmwareVersion = preferences.get(DiaconnStringNonKey.PumpVersion)

            if (pumpFirmwareVersion.isNotEmpty() && PumpLogUtil.isPumpVersionGe(pumpFirmwareVersion, 3, 0)) {
                sendMessage(BigAPSMainInfoInquirePacket(injector)) // APS Pump Main Info
            } else {
                sendMessage(BasalLimitInquirePacket(injector)) // basal Limit
                sendMessage(SnackLimitInquirePacket(injector)) // bolus Limit
                sendMessage(BigMainInfoInquirePacket(injector)) // Pump Main Info
                sendMessage(SoundInquirePacket(injector)) // sounds
                sendMessage(DisplayTimeInquirePacket(injector)) // display
                sendMessage(LanguageInquirePacket(injector)) // language
            }

            diaconnG8Pump.lastConnection = System.currentTimeMillis()

            val profile = pumpSync.expectedPumpState().profile
            if (profile != null && abs(diaconnG8Pump.baseAmount - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))

                if (!diaconnG8Plugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileChangeRequested())
                }
            }

            // time setting
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)))
            // A clock read now, not the one of the main info: each message since then moved it further off.
            diaconnG8Pump.clearPumpClock()
            sendMessage(TimeInquirePacket(injector))
            if (diaconnG8Pump.getPumpTime() == 0L && !diaconnG8Pump.pumpClockImpossible) {
                // initial handshake was not successful
                // de-initialize pump
                diaconnG8Pump.reset()
                rxBus.send(EventDiaconnG8NewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            // Null: the pump has no real time, as a clock never set that showed 00:70. Then there is no time to keep.
            val timeDiff = diaconnG8Pump.pumpClockOffsetSeconds()
            aapsLogger.debug(LTag.PUMPCOMM, if (timeDiff == null) "The pump has no real time" else "Pump time difference: $timeDiff seconds")
            if (timeDiff != null && abs(timeDiff) > 60 * 60 * 1.5) {
                aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                //If time-diff is very large, warn user until we can synchronize history readings properly
                uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), AlarmSound.ERROR)

                //de-initialize pump
                diaconnG8Pump.reset()
                rxBus.send(EventDiaconnG8NewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            if ((timeDiff == null || timeDiff > 0 || abs(timeDiff) > 60) && !diaconnG8Pump.isTempBasalInProgress) {
                val msgPacket = TimeSettingPacket(injector, dateUtil.now())
                sendMessage(msgPacket)

                // otp process. With no answer the clock read below tells whether it was set.
                if (processConfirm(msgPacket.msgType) == Confirm.NOT_DONE) return

                diaconnG8Pump.clearPumpClock()
                sendMessage(TimeInquirePacket(injector))
                aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference after setting it: ${diaconnG8Pump.pumpClockOffsetSeconds()} seconds")
            }
            if (diaconnG8Pump.getPumpTime() == 0L) {
                // Still no real time on the pump: it was not set while a temp basal ran. The pump history would get wrong
                // times, so the user is told, as for a clock far off. With no answer to the clock read, it is only tried
                // again at the next connection.
                if (diaconnG8Pump.pumpClockImpossible)
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), AlarmSound.ERROR)
                diaconnG8Pump.reset()
                rxBus.send(EventDiaconnG8NewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            loadHistory()
            // The pump history gives the exact times of a stop of the insulin on the pump. The status (1 = stopped) ends or
            // starts what the history did not give, as for Dana.
            PumpSuspendSync.followStatus(pumpSync, aapsLogger, diaconnG8Pump.basePauseStatus == 1, dateUtil.now(), PumpType.DIACONN_G8, diaconnG8Pump.serialNo.toString())
            // Sync TBR/EB state directly from pump response fields instead of pumpSync.expectedPumpState()
            // This prevents timing issues where pumpSync DB may not be updated yet
            diaconnG8Pump.syncTempBasalFromPump()
            diaconnG8Pump.syncExtendedBolusFromPump()
            // The temp basal the pump runs against the one AAPS has, while the pump gives insulin.
            if (diaconnG8Pump.basePauseStatus != 1)
                DiaconnTempBasalSync.followStatus(
                    pumpSync = pumpSync,
                    aapsLogger = aapsLogger,
                    pumpRuns = diaconnG8Pump.isTempBasalInProgress,
                    pumpStart = diaconnG8Pump.tempBasalStart,
                    pumpEnd = diaconnG8Pump.tempBasalStart + diaconnG8Pump.tempBasalDuration,
                    rate = diaconnG8Pump.tempBasalAbsoluteRate,
                    notBefore = diaconnG8Pump.lastPumpRestart,
                    now = dateUtil.now(),
                    serial = diaconnG8Pump.serialNo.toString()
                )
            rxBus.send(EventDiaconnG8NewStatus())
            rxBus.send(EventInitializationChanged())
            if (diaconnG8Pump.dailyTotalUnits > diaconnG8Pump.maxDailyTotalUnits * Constants.DAILY_RESERVOIR_LIMIT_WARNING) {
                aapsLogger.debug(LTag.PUMPCOMM, "Approaching daily limit: " + diaconnG8Pump.dailyTotalUnits + "/" + diaconnG8Pump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    notificationManager.post(NotificationId.APPROACHING_DAILY_LIMIT, TextRef.AndroidRes(R.string.approachingdailylimit))
                    pumpSync.insertAnnouncement(
                        rh.gs(app.aaps.core.interfaces.R.string.confirmation_line, rh.gs(R.string.approachingdailylimit), rh.gs(app.aaps.core.interfaces.R.string.values_with_slash, diaconnG8Pump.dailyTotalUnits.toString(), rh.gs(app.aaps.core.ui.R.string.units_format_insulin_int, diaconnG8Pump.maxDailyTotalUnits))),
                        null,
                        PumpType.DIACONN_G8,
                        diaconnG8Pump.serialNo.toString()
                    )
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception", e)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump status loaded")
    }

    /**
     * Reads the pump logs that are newer than the last log the app has stored. When the user turned it on, it also
     * uploads the pump logs to the Diaconn cloud.
     */
    fun loadHistory(): PumpEnactResult {
        if (!diaconnG8Plugin.isInitialized()) {
            val result = pumpEnactResultProvider().success(false)
            result.comment = "pump not initialized"
            return result
        }
        val result = pumpEnactResultProvider()
        val logStatus = LogStatusInquirePacket(injector)
        sendMessage(logStatus)
        if (!logStatus.isReceived) return result.success(false).comment("no answer to the log status")
        // pump version check
        if (diaconnG8Pump.isPumpVersionGe2_63) {
            sendMessage(IncarnationInquirePacket(injector))
        }

        var apsLastLogNum = 9999
        var apsWrappingCount = -1
        // get saved last log info
        val diaconnHistoryRecord = diaconnHistoryRecordDao.getLastRecord(diaconnG8Pump.pumpUid)
        aapsLogger.debug(LTag.PUMPCOMM, "diaconnHistoryRecord :: $diaconnHistoryRecord")

        if (diaconnHistoryRecord != null) {
            apsLastLogNum = diaconnHistoryRecord.lognum
            apsWrappingCount = diaconnHistoryRecord.wrappingCount
        }

        // pump log status
        val pumpLastNum = diaconnG8Pump.pumpLastLogNum
        val pumpWrappingCount = diaconnG8Pump.pumpWrappingCount
        val apsIncarnationNum = preferences.get(DiaconnIntNonKey.ApsIncarnationNo)
        // aps last log num
        val pumpSerialNo = preferences.get(DiaconnIntNonKey.PumpSerialNo)

        // if first install app
        if (apsWrappingCount == -1 && apsLastLogNum == 9999) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
            aapsLogger.debug(LTag.PUMPCOMM, "first install app apsWrappingCount : $apsWrappingCount, apsLastLogNum : $apsLastLogNum")
        }
        // if another pump
        if (pumpSerialNo != diaconnG8Pump.serialNo) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
            preferences.put(DiaconnIntNonKey.PumpSerialNo, diaconnG8Pump.serialNo)
            aapsLogger.debug(LTag.PUMPCOMM, "Pump serialNo is different apsWrappingCount : $apsWrappingCount, apsLastLogNum : $apsLastLogNum")
        }
        // if pump reset
        if (apsIncarnationNum != diaconnG8Pump.pumpIncarnationNum) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
            preferences.put(DiaconnIntNonKey.ApsIncarnationNo, diaconnG8Pump.pumpIncarnationNum)
            aapsLogger.debug(LTag.PUMPCOMM, "Pump incarnationNum is different apsWrappingCount : $apsWrappingCount, apsLastLogNum : $apsLastLogNum")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "apsWrappingCount : $apsWrappingCount, apsLastLogNum : $apsLastLogNum")

        // pump log loop size
        val pumpLogPageSize = 11
        val ranges = logRanges(apsLastLogNum, apsWrappingCount, pumpLastNum, pumpWrappingCount)
        aapsLogger.debug(LTag.PUMPCOMM, "log ranges to read: $ranges")
        // log sync start!
        // A read that finds nothing new is a good read too. It failed, so the daily totals of a pump with no new record failed.
        // A page with no answer ends the read: the next read starts after the last stored record, so a later page read now
        // would skip the lost one for good.
        var allPagesRead = true
        pages@ for ((start, end) in ranges) {
            for (startLogNo in start until end step pumpLogPageSize) {
                val endLogNo: Int = startLogNo + min(end - startLogNo, pumpLogPageSize)
                aapsLogger.debug(LTag.PUMPCOMM, "pumplog request : $startLogNo ~ $endLogNo")
                val msg = BigLogInquirePacket(injector, startLogNo, endLogNo, 100)
                sendMessage(msg, 2000)
                if (!msg.isReceived) {
                    aapsLogger.debug(LTag.PUMPCOMM, "no answer to the log page $startLogNo ~ $endLogNo, the read ends")
                    allPagesRead = false
                    break@pages
                }
            }
        }
        result.success(allPagesRead)
        if (allPagesRead) diaconnG8Pump.lastConnection = System.currentTimeMillis()
        // upload pump log to Diaconn Cloud
        // Never from the emulator: it sent the log of an emulated pump to the Diaconn cloud, under the identity of a real one.
        if (preferences.get(DiaconnBooleanKey.SendLogsToCloud) && !config.isEnabled(ExternalOptions.EMULATE_DIACONN)) {
            SystemClock.sleep(1000)
            try {
                // getting last uploaded log number
                val retrofit = diaconnLogUploader.getRetrofitInstance()
                val api = retrofit?.create(DiaconnApiService::class.java)
                val response = api?.getPumpLastNo(diaconnG8Pump.pumpUid, diaconnG8Pump.pumpVersion, diaconnG8Pump.pumpIncarnationNum)?.execute()
                if (response?.body()?.ok == true) {
                    aapsLogger.debug(LTag.PUMPCOMM, "pumplog_no = ${response.body()?.info?.pumplog_no}")
                    val platformLastNo = response.body()?.info?.pumplog_no!!
                    val platformWrappingCount: Int = floor(platformLastNo / 10000.0).toInt()
                    val platformLogNo: Int = if (platformLastNo.toInt() == -1) {
                        9999
                    } else {
                        (platformLastNo % 10000).toInt()
                    }
                    aapsLogger.debug(LTag.PUMPCOMM, "platformLogNo: $platformLogNo, platformWrappingCount: $platformWrappingCount")

                    // number of loops when the logs are read one page at a time
                    val (platformStart, platformEnd, platformLoopSize) = getCloudLogLoopCount(platformLastNo.toInt(), platformLogNo, platformWrappingCount, pumpLastNum, pumpWrappingCount)
                    if (platformLoopSize > 0) {
                        diaconnG8Pump.isPlatformUploadStarted = true
                        for (i in 0 until platformLoopSize) {
                            if (diaconnG8Pump.isPumpLogUploadFailed) {
                                break
                            }
                            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.diaconn_g8_cloud_sync_progress, i, platformLoopSize)))
                            val startLogNo: Int = platformStart + i * pumpLogPageSize
                            val endLogNo: Int = startLogNo + min(platformEnd - startLogNo, pumpLogPageSize)
                            val msg = BigLogInquirePacket(injector, startLogNo, endLogNo, 100)
                            sendMessage(msg, 500)
                        }
                        SystemClock.sleep(1000)
                        diaconnG8Pump.isPlatformUploadStarted = false
                        diaconnG8Pump.isPumpLogUploadFailed = false
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
            }
        }
        return result
    }

    private fun getCloudLogLoopCount(platformLastNo: Int, platformPumpLogNum: Int, wrappingCount: Int, pumpLastNum: Int, pumpWrappingCount: Int): Triple<Int, Int, Int> {
        val start: Int// log sync start number
        val end: Int // log sync end number
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "platformLastNo: $platformLastNo, PlatformPumpLogNum : $platformPumpLogNum, wrappingCount : $wrappingCount , pumpLastNum: $pumpLastNum, pumpWrappingCount :$pumpWrappingCount"
        )

        if ((pumpWrappingCount * 10000 + pumpLastNum - platformLastNo > 10000)) {
            start = pumpLastNum
            end = 10000
        } else if (pumpWrappingCount > wrappingCount && platformPumpLogNum < 9999) {
            start = (platformPumpLogNum + 1)
            end = 10000
        } else if (pumpWrappingCount > wrappingCount && platformPumpLogNum >= 9999) {
            start = 0 // start from the beginning
            end = pumpLastNum
        } else {
            start = (platformPumpLogNum + 1)
            end = pumpLastNum
        }
        val size = ceil((end - start) / 11.0).toInt()
        //
        return Triple(start, end, size)
    }

    fun setUserSettings(): PumpEnactResult {
        val result = pumpEnactResultProvider()

        val msg: DiaconnG8Packet = when (diaconnG8Pump.setUserOptionType) {
            DiaconnG8Pump.ALARM       -> SoundSettingPacket(injector, diaconnG8Pump.beepAndAlarm, diaconnG8Pump.alarmIntensity)
            DiaconnG8Pump.LCD         -> DisplayTimeoutSettingPacket(injector, diaconnG8Pump.lcdOnTimeSec)
            DiaconnG8Pump.LANG        -> LanguageSettingPacket(injector, diaconnG8Pump.selectedLanguage)
            DiaconnG8Pump.BOLUS_SPEED -> BolusSpeedSettingPacket(injector, diaconnG8Pump.bolusSpeed)
            else                      -> null
        } ?: return result.success(false)

        sendMessage(msg)
        // It looked at the confirmation code at once, before the pump could send it, and reported a setting done whatever
        // the pump answered to its confirm.
        return result.success(processConfirm(msg.msgType) == Confirm.DONE)
    }

    fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (bolusProgressData.isStopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.startingbolus)))

        // aps speed check
        if (updateBolusSpeedInPump) {
            val msg = BolusSpeedSettingPacket(injector, preferences.get(DiaconnIntKey.BolusSpeed))
            sendMessage(msg)
            // The speed the pump runs is read below, so a speed it did not take only makes the progress less exact.
            processConfirm(msg.msgType)
            updateBolusSpeedInPump = false
        }

        val insulin = detailedBolusInfo.insulin
        // pump bolus speed inquire
        sendMessage(BolusSpeedInquirePacket(injector))
        diaconnG8Pump.bolusDone = false
        diaconnG8Pump.bolusGivenInFull = false
        diaconnG8Pump.bolusingDetailedBolusInfo = detailedBolusInfo
        diaconnG8Pump.bolusStopped = false
        diaconnG8Pump.bolusStopForced = false
        diaconnG8Pump.bolusProgressLastTimeStamp = dateUtil.now()
        val start = InjectionSnackSettingPacket(injector, hundredths(insulin))
        val bolusStart = System.currentTimeMillis()
        if (insulin > 0) {
            if (!diaconnG8Pump.bolusStopped) {
                sendMessage(start, 100)
                // With no answer to the confirm the pump may give the bolus: its end report tells, as after a taken one.
                // It returned success for it without waiting, so a bolus the pump never gave was reported as given.
                if (processConfirm(start.msgType) == Confirm.NOT_DONE) return false
            } else {
                return false
            }
        }
        val bolusDurationInMSec = (insulin * diaconnG8Pump.secondsPerUnit * 1000).toLong()
        val expectedEnd = bolusStart + bolusDurationInMSec + 3500L
        val totalwaitTime = (expectedEnd - System.currentTimeMillis()) / 1000
        // reset bolus progress history
        diaconnG8Pump.bolusingInjProgress = 0
        diaconnG8Pump.bolusingSetAmount = 0.0
        diaconnG8Pump.bolusingInjAmount = 0.0

        // The end of the bolus is only known from the pump's final report. If that report is lost, or
        // the link drops after the start was confirmed, this loop used to wait forever and stall the
        // whole command queue (#5209). Give up when the link is gone, or a minute after the bolus would
        // have ended at the slowest pump speed (60 s/U), and report it as broken communication - like
        // DanaR does. Not from expectedEnd: that uses the speed the pump reported, and if that inquiry
        // got no answer, a slow bolus could still be running normally. The real amount is then read from
        // the pump history by the loadEvents() below.
        val giveUpAt = bolusStart + (insulin * 60 * 1000).toLong() + 60 * 1000L
        var connectionBroken = false
        if (insulin > 0) {
            while (!diaconnG8Pump.bolusDone) {
                if (!isConnected || System.currentTimeMillis() > giveUpAt) {
                    aapsLogger.error(LTag.PUMPCOMM, "Bolus end not received (connected=$isConnected), giving up")
                    connectionBroken = true
                    break
                }
                if (diaconnG8Pump.isPumpVersionGe3_53) {
                    val delivered = PumpInsulin(diaconnG8Pump.bolusingInjAmount)
                    bolusProgressData.updateProgress(delivered = delivered)
                } else {
                    var progressPercent = 0
                    val waitTime = (expectedEnd - System.currentTimeMillis()) / 1000
                    if (totalwaitTime > waitTime && totalwaitTime > 0) {
                        progressPercent = ((totalwaitTime - waitTime) * 100 / totalwaitTime).toInt()
                    }
                    val percent = min(progressPercent, 100)
                    bolusProgressData.updateProgress(percent, TextRef.AndroidRes(R.string.waitingforbolusend))
                }
                SystemClock.sleep(200)
            }
        }

        // do not call loadHistory() directly, reconnection may be needed. Queued before this command ends (UNDISPATCHED),
        // so the queue is never empty between the two.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            commandQueue.loadEvents()
            // reread bolus status
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            sendMessage(InjectionSnackInquirePacket(injector), 2000) // last bolus
            rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.interfaces.R.string.disconnecting)))
        }
        diaconnG8Pump.bolusingDetailedBolusInfo = null
        // A bolus the pump stopped itself, as at an occlusion, is not given: it was reported as given, so the user was
        // not told. A stop by the user is no failure.
        val stoppedByPump = diaconnG8Pump.bolusStoppedByPump
        if (stoppedByPump) aapsLogger.warn(LTag.PUMPCOMM, "The pump stopped the bolus of $insulin U: ${diaconnG8Pump.lastBolusAmount} U given")
        return !connectionBroken && !stoppedByPump
    }

    fun bolusStop() {
        val stop = InjectionCancelSettingPacket(injector, 0x07.toByte())
        diaconnG8Pump.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop, 100)
            // otp process
            if (processConfirm(stop.msgType) == Confirm.NOT_DONE) return
            // Bounded like DanaR's bolusStop: a lost stop report or a link drop must not keep this
            // thread waiting forever (#5209).
            val giveUpAt = System.currentTimeMillis() + 10 * 1000L
            while (!diaconnG8Pump.bolusStopped && isConnected && System.currentTimeMillis() < giveUpAt) {
                SystemClock.sleep(200)
            }
            if (!diaconnG8Pump.bolusStopped) {
                aapsLogger.warn(LTag.PUMPCOMM, "bolusStop: no stop report (connected=$isConnected), treating as stopped after timeout")
                diaconnG8Pump.bolusStopped = true
            }
        } else {
            diaconnG8Pump.bolusStopped = true
        }
    }

    fun tempBasal(absoluteRate: Double, durationInHours: Double): Boolean {
        if (!isConnected) return false

        val steps = ((durationInHours * 60) / 15).toInt()
        val tbInjectRate = hundredths(absoluteRate) + 1000
        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        val confirm: Confirm
        if (diaconnG8Pump.isPumpVersionGe3_53) {
            val tbrPacket = TempBasalSettingPacket(injector, 3, steps, tbInjectRate)
            sendMessage(tbrPacket, 100)
            confirm = processConfirm(tbrPacket.msgType)
        } else {

            if (diaconnG8Pump.tbStatus == 1) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
                val tbrPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
                // tempbasal stop
                sendMessage(tbrPacket, 100)
                // otp process. With no answer the new temp basal below replaces the old one anyway.
                if (processConfirm(tbrPacket.msgType) == Confirm.NOT_DONE) return false
                diaconnG8Pump.tempBasalStart = dateUtil.now()
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
            val tbrPacket = TempBasalSettingPacket(injector, 1, steps, tbInjectRate)
            sendMessage(tbrPacket, 100)
            confirm = processConfirm(tbrPacket.msgType)
        }
        if (confirm == Confirm.NOT_DONE) return false

        // With no answer to the confirm, only the pump tells whether it took the temp basal. It reported every temp basal
        // set, whatever the pump answered.
        val started = confirm == Confirm.DONE || pumpShows({ sendMessage(TempBasalInquirePacket(injector)) }) {
            diaconnG8Pump.tbStatus == 1 && diaconnG8Pump.tbInjectRateRatio == tbInjectRate
        }
        // Its log record, which AAPS records from, is there only then.
        if (confirm == Confirm.DONE) awaitCarriedOut("Temp basal")
        // Set TBR state directly after successful setting (don't rely solely on loadHistory/pumpSync)
        if (started) {
            val durationInMinutes = (durationInHours * 60).toLong()
            diaconnG8Pump.tempBasalStart = dateUtil.now()
            diaconnG8Pump.tempBasalDuration = T.mins(durationInMinutes).msecs()
            diaconnG8Pump.tempBasalAbsoluteRate = absoluteRate
            aapsLogger.debug(LTag.PUMPCOMM, "TBR set directly: start=${diaconnG8Pump.tempBasalStart}, duration=${durationInMinutes}min, rate=${absoluteRate}U/h")
        }

        // do not call loadHistory() directly, reconnection may be needed. Queued before this command ends (UNDISPATCHED),
        // so the queue is never empty between the two.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            commandQueue.loadEvents()
            rxBus.send(EventDiaconnG8NewStatus())
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return started
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        if (diaconnG8Pump.tbStatus == 1) {
            val msgPacket = TempBasalSettingPacket(
                injector,
                2,
                diaconnG8Pump.tbTime,
                diaconnG8Pump.tbInjectRateRatio
            )
            // tempbasal stop
            sendMessage(msgPacket, 500)
            // otp process
            val confirm = processConfirm(msgPacket.msgType)
            if (confirm == Confirm.NOT_DONE) return false
            // Only the pump tells whether the stop came through.
            if (confirm == Confirm.NO_ANSWER && !pumpShows({ sendMessage(TempBasalInquirePacket(injector)) }) { diaconnG8Pump.tbStatus != 1 }) return false
            // In place of a fixed 0.5 s: its log record is there only when the pump has carried it out.
            if (confirm == Confirm.DONE) awaitCarriedOut("Temp basal stop")

            // Clear TBR state directly after successful stop
            diaconnG8Pump.tempBasalStart = 0L
            diaconnG8Pump.tempBasalDuration = 0L
            diaconnG8Pump.tempBasalAbsoluteRate = 0.0
            aapsLogger.debug(LTag.PUMPCOMM, "TBR stopped and state cleared directly")
        }

        // do not call loadHistory() directly, reconnection may be needed. Queued before this command ends (UNDISPATCHED),
        // so the queue is never empty between the two.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            commandQueue.loadEvents()
            rxBus.send(EventDiaconnG8NewStatus())
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun extendedBolus(insulin: Double, durationInMinutes: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        aapsLogger.debug(LTag.PUMPCOMM, "insulin: $insulin durationInMinutes: $durationInMinutes")

        val msgExtended = InjectionExtendedBolusSettingPacket(injector, hundredths(insulin), durationInMinutes, dateUtil.now())
        sendMessage(msgExtended)
        // otp process. It reported every extended bolus set, whatever the pump answered.
        val started = when (processConfirm(msgExtended.msgType)) {
            // Its log record, which AAPS records from, is there only when the pump has carried it out.
            Confirm.DONE      -> true.also { awaitCarriedOut("Extended bolus") }
            Confirm.NOT_DONE  -> false
            // Only the pump tells whether it took the extended bolus.
            Confirm.NO_ANSWER -> pumpShows({ readMainInfo() }) { diaconnG8Pump.squareStatus == SQUARE_RUNNING }
        }

        // Set EB state directly after successful setting (don't rely solely on loadHistory/pumpSync)
        if (started) {
            diaconnG8Pump.extendedBolusStart = dateUtil.now()
            diaconnG8Pump.extendedBolusDuration = T.mins(durationInMinutes.toLong()).msecs()
            diaconnG8Pump.extendedBolusAmount = insulin
            aapsLogger.debug(LTag.PUMPCOMM, "EB set directly: start=${diaconnG8Pump.extendedBolusStart}, duration=${durationInMinutes}min, amount=${insulin}U")
        }

        // do not call loadHistory() directly, reconnection may be needed. Queued before this command ends (UNDISPATCHED),
        // so the queue is never empty between the two.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            commandQueue.loadEvents()
            rxBus.send(EventDiaconnG8NewStatus())
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return started
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        val msgType = if (diaconnG8Pump.dualStatus == 1) 0x09.toByte() else 0x08.toByte()
        val msgStop = InjectionCancelSettingPacket(injector, msgType)
        sendMessage(msgStop)
        // otp process
        val stopped = when (processConfirm(msgStop.msgType)) {
            Confirm.DONE      -> true.also { awaitCarriedOut("Extended bolus stop") }
            Confirm.NOT_DONE  -> false
            // Only the pump tells whether the stop came through.
            Confirm.NO_ANSWER -> pumpShows({ readMainInfo() }) { diaconnG8Pump.squareStatus != SQUARE_RUNNING }
        }
        if (!stopped) return false

        // Clear EB state directly after successful stop
        diaconnG8Pump.extendedBolusStart = 0L
        diaconnG8Pump.extendedBolusDuration = 0L
        diaconnG8Pump.extendedBolusAmount = 0.0
        aapsLogger.debug(LTag.PUMPCOMM, "EB stopped and state cleared directly")

        // do not call loadHistory() directly, reconnection may be needed. Queued before this command ends (UNDISPATCHED),
        // so the queue is never empty between the two.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            commandQueue.loadEvents()
            rxBus.send(EventDiaconnG8NewStatus())
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    /**
     * Reads the pump with [read] until [shows] holds, for a few seconds. A real G8 carries out a setting 1.2 to 1.9 s
     * after the answer to its confirm (2026-11-11). It mostly holds a read until then, but it answered one right after the
     * start of an extended bolus with the state before it.
     */
    private fun pumpShows(read: () -> Unit, shows: () -> Boolean): Boolean {
        repeat(STATE_READS) {
            read()
            if (shows()) return true
            SystemClock.sleep(STATE_READ_GAP_MS)
        }
        return false
    }

    /** Reads the main info of the pump: what it runs now, as its extended bolus. Firmware 3.0 and later has its own one. */
    private fun readMainInfo() {
        val pumpFirmwareVersion = preferences.get(DiaconnStringNonKey.PumpVersion)
        if (pumpFirmwareVersion.isNotEmpty() && PumpLogUtil.isPumpVersionGe(pumpFirmwareVersion, 3, 0))
            sendMessage(BigAPSMainInfoInquirePacket(injector))
        else
            sendMessage(BigMainInfoInquirePacket(injector))
    }

    suspend fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basalList = diaconnG8Pump.buildDiaconnG8ProfileRecord(profile)

        val requestReqPacket1 = BasalSettingPacket(
            injector,
            1,
            1,
            (basalList[0] * 100).toInt(),
            (basalList[1] * 100).toInt(),
            (basalList[2] * 100).toInt(),
            (basalList[3] * 100).toInt(),
            (basalList[4] * 100).toInt(),
            (basalList[5] * 100).toInt()
        )
        val requestReqPacket2 = BasalSettingPacket(
            injector,
            1,
            2,
            (basalList[6] * 100).toInt(),
            (basalList[7] * 100).toInt(),
            (basalList[8] * 100).toInt(),
            (basalList[9] * 100).toInt(),
            (basalList[10] * 100).toInt(),
            (basalList[11] * 100).toInt()
        )
        val requestReqPacket3 = BasalSettingPacket(
            injector,
            1,
            3,
            (basalList[12] * 100).toInt(),
            (basalList[13] * 100).toInt(),
            (basalList[14] * 100).toInt(),
            (basalList[15] * 100).toInt(),
            (basalList[16] * 100).toInt(),
            (basalList[17] * 100).toInt()
        )
        val requestReqPacket4 = BasalSettingPacket(
            injector,
            1,
            4,
            (basalList[18] * 100).toInt(),
            (basalList[19] * 100).toInt(),
            (basalList[20] * 100).toInt(),
            (basalList[21] * 100).toInt(),
            (basalList[22] * 100).toInt(),
            (basalList[23] * 100).toInt()
        )
        // setting basal pattern 1,2,3,4
        sendMessage(SerialNumInquirePacket(injector), 2000)
        sendMessage(requestReqPacket1, 500)
        sendMessage(requestReqPacket2, 500)
        sendMessage(requestReqPacket3, 500)
        sendMessage(requestReqPacket4)

        // otp process
        val basalConfirm = processConfirm(requestReqPacket4.msgType)
        if (basalConfirm == Confirm.NOT_DONE) return false
        // pump saving time about 30 second
        aapsLogger.debug(LTag.PUMPCOMM, "30 seconds Waiting!!")
        SystemClock.sleep(30000)

        val msgPacket = InjectionBasalSettingPacket(injector, 1)
        sendMessage(msgPacket)
        // otp process
        val activateConfirm = processConfirm(msgPacket.msgType)
        if (activateConfirm == Confirm.NOT_DONE) return false
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        if (basalConfirm == Confirm.DONE && activateConfirm == Confirm.DONE) return true
        // A confirm with no answer: the basal the status read just read back tells whether the pump took the profile.
        // It reported every profile set, whatever the pump answered.
        val onPump = diaconnG8Pump.pumpProfiles?.get(diaconnG8Pump.activeProfile) ?: return false
        return basalList.indices.all { abs(onPump[it] - basalList[it]) < BASAL_TOLERANCE }
    }

    /** What the pump said to the confirm of a setting. A setting takes effect only with its confirm. */
    private enum class Confirm {

        /** The pump took the setting. */
        DONE,

        /** The pump does not do it: no confirmation code came for the setting, or the pump refused the confirm. */
        NOT_DONE,

        /** The confirm got no answer: the pump may have taken the setting, only a read of the pump tells. */
        NO_ANSWER
    }

    /**
     * Sends the confirm of the setting [msgType] with the confirmation code (OTP) from the setting's answer.
     *
     * It reported done for every confirm it sent, and the answer to it was never read. So a bolus or a temp basal the pump
     * refused, or whose confirm was lost, was reported as given.
     */
    private fun processConfirm(msgType: Byte): Confirm {
        // pump confirm
        var loopCnt = 0
        // waits up to 3 seconds for the OTP (30 loops of 100 ms)
        while (loopCnt < 30 && diaconnG8Pump.otpNumber == 0) {
            SystemClock.sleep(100)
            aapsLogger.debug(LTag.PUMPCOMM, "OTP waiting 100ms $loopCnt / 30")
            loopCnt++
        }
        // check if otp received
        if (diaconnG8Pump.otpNumber == 0) {
            aapsLogger.error(LTag.PUMPCOMM, "otp is not received yet after ${loopCnt * 100}ms")
            return Confirm.NOT_DONE
        }
        aapsLogger.debug(LTag.PUMPCOMM, "OTP received: ${diaconnG8Pump.otpNumber} after ${loopCnt * 100}ms")
        diaconnG8Pump.confirmResult = null
        confirmReportsBefore = diaconnG8Pump.confirmReports
        val confirm = AppConfirmSettingPacket(injector, msgType, diaconnG8Pump.otpNumber)
        sendMessage(confirm, 2000)
        diaconnG8Pump.otpNumber = 0
        val result = when {
            !confirm.isReceived                 -> Confirm.NO_ANSWER
            diaconnG8Pump.confirmResult == 0 -> Confirm.DONE
            else                                -> Confirm.NOT_DONE
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Confirm of 0x${"%02X".format(msgType)}: $result (pump result ${diaconnG8Pump.confirmResult})")
        return result
    }

    /** The count of confirm reports when the last confirm was sent. */
    private var confirmReportsBefore = 0

    /**
     * Waits until the pump has carried out the setting it last confirmed, at most [CARRY_OUT_WAIT_MS]. The pump says so
     * with a confirm report. Real G8 fw 2.50 (2026-11-11): a temp basal or an extended bolus is carried out 1.2 to 1.9 s
     * after the answer to its confirm, and only then is its log record written. A log read right after the start of an
     * extended bolus came before that, so AAPS had no extended bolus until the next read, and the user could not cancel it.
     */
    private fun awaitCarriedOut(what: String) {
        val start = System.currentTimeMillis()
        while (diaconnG8Pump.confirmReports == confirmReportsBefore && isConnected && System.currentTimeMillis() - start < CARRY_OUT_WAIT_MS)
            SystemClock.sleep(CARRY_OUT_POLL_MS)
        if (diaconnG8Pump.confirmReports != confirmReportsBefore) aapsLogger.debug(LTag.PUMPCOMM, "$what carried out after ${System.currentTimeMillis() - start} ms")
        else aapsLogger.warn(LTag.PUMPCOMM, "$what: no confirm report from the pump in ${System.currentTimeMillis() - start} ms")
    }

    companion object {

        /** The pump numbers its log 0 until this, then starts again at 0 with a higher wrap count. */
        private const val LOG_NUMBERS = 10000

        /** The square (extended) bolus status of the main info while one runs. */
        private const val SQUARE_RUNNING = 1

        /** How long `awaitCarriedOut` waits for the pump: it took up to 1.9 s. */
        private const val CARRY_OUT_WAIT_MS = 3_000L
        private const val CARRY_OUT_POLL_MS = 50L

        /** How often and how far apart the pump is read after a confirm with no answer: about 5 s in all. */
        private const val STATE_READS = 8
        private const val STATE_READ_GAP_MS = 600L

        /** The pump keeps a rate in hundredths of a unit; the record sent to it adds 0.00001 against a cut. */
        private const val BASAL_TOLERANCE = 0.005

        /**
         * [units] in the hundredths the pump takes, rounded. A cut gave less: 0.29 * 100 is 28.99999..., so a bolus of
         * 0.29 U went to the pump as 0.28 U.
         */
        internal fun hundredths(units: Double): Int = (units * 100).roundToInt()

        /**
         * The log numbers to read, as ranges from start (read) to end (not read), oldest first. [lastLogNum] and
         * [wrappingCount] are the newest record AAPS has, [pumpLastNum] is one past the newest record of the pump.
         *
         * After a wrap the old wrap is read to its end and the new one from 0, in the same read. It read the old wrap to
         * its end and then nothing more: AAPS records a bolus only from the pump log, so every bolus after it was lost.
         * More than a whole log behind, it starts at the newest records, as after a first pairing: the whole log is 910
         * pages of 2 s each.
         */
        internal fun logRanges(lastLogNum: Int, wrappingCount: Int, pumpLastNum: Int, pumpWrappingCount: Int): List<Pair<Int, Int>> =
            when (pumpWrappingCount - wrappingCount) {
                0    -> listOf(lastLogNum + 1 to pumpLastNum)
                1    -> listOf(lastLogNum + 1 to LOG_NUMBERS, 0 to pumpLastNum)
                // A lower wrap count: the pump was reset. The serial and incarnation checks in loadHistory handle that.
                in Int.MIN_VALUE..-1 -> listOf(lastLogNum + 1 to pumpLastNum)
                else -> listOf(max(pumpLastNum - 2, 0) to pumpLastNum)
            }.filter { (start, end) -> end > start }
    }
}