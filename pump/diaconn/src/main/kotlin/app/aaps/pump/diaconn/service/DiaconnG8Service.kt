package app.aaps.pump.diaconn.service

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.diaconn.DiaconnG8Pump
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
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class DiaconnG8Service : DaggerService() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var diaconnG8Plugin: DiaconnG8Plugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var bleCommonService: BLECommonService
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var diaconnLogUploader: DiaconnLogUploader
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private var lastApproachingDailyLimit: Long = 0

    private var updateBolusSpeedInPump = false

    override fun onCreate() {
        super.onCreate()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopSelf() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.isChanged(DiaconnIntKey.BolusSpeed.key)) {
                               diaconnG8Pump.bolusSpeed = preferences.get(DiaconnIntKey.BolusSpeed)
                               diaconnG8Pump.speed = preferences.get(DiaconnIntKey.BolusSpeed)
                               updateBolusSpeedInPump = true
                           }
                       }, fabricPrivacy::logException)
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
        disposable.clear()
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

    fun readPumpStatus() {
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

            val profile = profileFunction.getProfile()
            if (profile != null && abs(diaconnG8Pump.baseAmount - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))

                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }

            // 시간 설정
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)))
            sendMessage(TimeInquirePacket(injector))
            var timeDiff = (diaconnG8Pump.getPumpTime() - System.currentTimeMillis()) / 1000L
            if (diaconnG8Pump.getPumpTime() == 0L) {
                // initial handshake was not successful
                // de-initialize pump
                diaconnG8Pump.reset()
                rxBus.send(EventDiaconnG8NewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
            // phone timezone
            val tz = DateTimeZone.getDefault()
            val instant = DateTime.now().millis
            val offsetInMilliseconds = tz.getOffset(instant).toLong()
            val offset = TimeUnit.MILLISECONDS.toHours(offsetInMilliseconds).toInt()
            if (timeDiff > 0 || abs(timeDiff) > 60) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), app.aaps.core.ui.R.raw.error)

                    //de-initialize pump
                    diaconnG8Pump.reset()
                    rxBus.send(EventDiaconnG8NewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {

                    if (!diaconnG8Pump.isTempBasalInProgress) {
                        val msgPacket = TimeSettingPacket(injector, dateUtil.now(), offset)
                        sendMessage(msgPacket)

                        // otp process
                        if (!processConfirm(msgPacket.msgType)) return

                        timeDiff = (diaconnG8Pump.getPumpTime() - System.currentTimeMillis()) / 1000L
                        aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
                    }
                }
            }
            loadHistory()
            val eb = pumpSync.expectedPumpState().extendedBolus
            diaconnG8Pump.fromExtendedBolus(eb)
            val tbr = pumpSync.expectedPumpState().temporaryBasal
            diaconnG8Pump.fromTemporaryBasal(tbr)
            rxBus.send(EventDiaconnG8NewStatus())
            rxBus.send(EventInitializationChanged())
            if (diaconnG8Pump.dailyTotalUnits > diaconnG8Pump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMPCOMM, "Approaching daily limit: " + diaconnG8Pump.dailyTotalUnits + "/" + diaconnG8Pump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(R.string.approachingdailylimit), Notification.URGENT)
                    pumpSync.insertAnnouncement(
                        rh.gs(R.string.approachingdailylimit) + ": " + diaconnG8Pump.dailyTotalUnits + "/" + diaconnG8Pump.maxDailyTotalUnits + "U",
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

    fun loadHistory(): PumpEnactResult {
        if (!diaconnG8Plugin.isInitialized()) {
            val result = pumpEnactResultProvider.get().success(false)
            result.comment = "pump not initialized"
            return result
        }
        sendMessage(LogStatusInquirePacket(injector))
        // pump version check
        if (diaconnG8Pump.isPumpVersionGe2_63) {
            sendMessage(IncarnationInquirePacket(injector))
        }

        val result = pumpEnactResultProvider.get()
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
        val (start, end, loopSize) = getLogLoopCount(apsLastLogNum, apsWrappingCount, pumpLastNum, pumpWrappingCount)
        aapsLogger.debug(LTag.PUMPCOMM, "loopinfo start : $start, end : $end, loopSize : $loopSize")
        // log sync start!
        if (loopSize > 0) {
            for (i in 0 until loopSize) {
                val startLogNo: Int = start + i * pumpLogPageSize
                val endLogNo: Int = startLogNo + min(end - startLogNo, pumpLogPageSize)
                aapsLogger.debug(LTag.PUMPCOMM, "pumplog request : $startLogNo ~ $endLogNo")
                val msg = BigLogInquirePacket(injector, startLogNo, endLogNo, 100)
                sendMessage(msg, 500)
            }
            result.success(true)
            diaconnG8Pump.lastConnection = System.currentTimeMillis()
        }
        // upload pump log to Diaconn Cloud
        if (preferences.get(DiaconnBooleanKey.SendLogsToCloud)) {
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

                    // 페이지 사이즈로 처리할 때 루핑 횟수 계산
                    val (platformStart, platformEnd, platformLoopSize) = getCloudLogLoopCount(platformLastNo.toInt(), platformLogNo, platformWrappingCount, pumpLastNum, pumpWrappingCount)
                    if (platformLoopSize > 0) {
                        diaconnG8Pump.isPlatformUploadStarted = true
                        for (i in 0 until platformLoopSize) {
                            if (diaconnG8Pump.isPumpLogUploadFailed) {
                                break
                            }
                            rxBus.send(EventPumpStatusChanged("클라우드동기화 진행 중 : $i / $platformLoopSize"))
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

    private fun getLogLoopCount(lastLogNum: Int, wrappingCount: Int, pumpLastNum: Int, pumpWrappingCount: Int): Triple<Int, Int, Int> {
        val start: Int// log sync start number
        val end: Int // log sync end number1311
        aapsLogger.debug(LTag.PUMPCOMM, "lastLogNum : $lastLogNum, wrappingCount : $wrappingCount , pumpLastNum: $pumpLastNum, pumpWrappingCount : $pumpWrappingCount")

        if (pumpWrappingCount > wrappingCount) {
            start = (lastLogNum + 1)
            end = 10000
        } else {
            start = (lastLogNum + 1)
            end = pumpLastNum
        }
        val size = ceil((end - start) / 11.0).toInt()
        //
        return Triple(start, end, size)
    }

    private fun getCloudLogLoopCount(platformLastNo: Int, platformPumpLogNum: Int, wrappingCount: Int, pumpLastNum: Int, pumpWrappingCount: Int): Triple<Int, Int, Int> {
        val start: Int// log sync start number
        val end: Int // log sync end number1311
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
            start = 0 // 처음부터 시작
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
        val result = pumpEnactResultProvider.get()

        val msg: DiaconnG8Packet = when (diaconnG8Pump.setUserOptionType) {
            DiaconnG8Pump.ALARM       -> SoundSettingPacket(injector, diaconnG8Pump.beepAndAlarm, diaconnG8Pump.alarmIntensity)
            DiaconnG8Pump.LCD         -> DisplayTimeoutSettingPacket(injector, diaconnG8Pump.lcdOnTimeSec)
            DiaconnG8Pump.LANG        -> LanguageSettingPacket(injector, diaconnG8Pump.selectedLanguage)
            DiaconnG8Pump.BOLUS_SPEED -> BolusSpeedSettingPacket(injector, diaconnG8Pump.bolusSpeed)
            else                      -> null
        } ?: return result.success(false)

        sendMessage(msg)
        // pump confirm
        if (diaconnG8Pump.otpNumber == 0) {
            aapsLogger.error(LTag.PUMPCOMM, "otp is not received yet")
            result.success(false)
            result.comment("")
            return result
        }
        sendMessage(AppConfirmSettingPacket(injector, msg.msgType, diaconnG8Pump.otpNumber))
        diaconnG8Pump.otpNumber = 0
        SystemClock.sleep(100)
        return result.success(true)
    }

    fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (BolusProgressData.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.startingbolus)))

        // aps speed check
        if (updateBolusSpeedInPump) {
            val msg = BolusSpeedSettingPacket(injector, preferences.get(DiaconnIntKey.BolusSpeed))
            sendMessage(msg)
            sendMessage(AppConfirmSettingPacket(injector, msg.msgType, diaconnG8Pump.otpNumber))
            diaconnG8Pump.otpNumber = 0
            updateBolusSpeedInPump = false
        }

        val insulin = detailedBolusInfo.insulin
        // pump bolus speed inquire
        sendMessage(BolusSpeedInquirePacket(injector))
        diaconnG8Pump.bolusDone = false
        diaconnG8Pump.bolusingDetailedBolusInfo = detailedBolusInfo
        diaconnG8Pump.bolusStopped = false
        diaconnG8Pump.bolusStopForced = false
        diaconnG8Pump.bolusProgressLastTimeStamp = dateUtil.now()
        val start = InjectionSnackSettingPacket(injector, (insulin * 100).toInt())
        val bolusStart = System.currentTimeMillis()
        if (insulin > 0) {
            if (!diaconnG8Pump.bolusStopped) {
                sendMessage(start, 100)
                // otp process
                if (!processConfirm(start.msgType)) return false
            } else {
                return false
            }
        }
        var speed = 12
        when (diaconnG8Pump.speed) {
            1 -> speed = 60
            2 -> speed = 30
            3 -> speed = 20
            4 -> speed = 15
            5 -> speed = 12
            6 -> speed = 10
            7 -> speed = 9
            8 -> speed = 8
        }

        val bolusDurationInMSec = (insulin * speed * 1000).toLong()
        val expectedEnd = bolusStart + bolusDurationInMSec + 3500L
        val totalwaitTime = (expectedEnd - System.currentTimeMillis()) / 1000
        // reset bolus progress history
        diaconnG8Pump.bolusingInjProgress = 0
        diaconnG8Pump.bolusingSetAmount = 0.0
        diaconnG8Pump.bolusingInjAmount = 0.0

        if (diaconnG8Pump.isReadyToBolus) {
            while (!diaconnG8Pump.bolusDone) {
                if (diaconnG8Pump.isPumpVersionGe3_53) {
                    rxBus.send(EventOverviewBolusProgress(rh, delivered = diaconnG8Pump.bolusingInjAmount, id = detailedBolusInfo.id))
                } else {
                    var progressPercent = 0
                    val waitTime = (expectedEnd - System.currentTimeMillis()) / 1000
                    if (totalwaitTime > waitTime && totalwaitTime > 0) {
                        progressPercent = ((totalwaitTime - waitTime) * 100 / totalwaitTime).toInt()
                    }
                    val percent = min(progressPercent, 100)
                    rxBus.send(
                        EventOverviewBolusProgress(
                            status = rh.gs(R.string.waitingforestimatedbolusend),
                            percent = percent,
                            id = detailedBolusInfo.id
                        )
                    )
                }
                SystemClock.sleep(200)
            }
        }
        diaconnG8Pump.isReadyToBolus = false

        // do not call loadHistory() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // reread bolus status
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
                sendMessage(InjectionSnackInquirePacket(injector), 2000) // last bolus
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.interfaces.R.string.disconnecting)))
            }
        })
        diaconnG8Pump.bolusingDetailedBolusInfo = null
        return !start.failed
    }

    fun bolusStop() {
        val stop = InjectionCancelSettingPacket(injector, 0x07.toByte())
        diaconnG8Pump.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop, 100)
            // otp process
            if (!processConfirm(stop.msgType)) return
            while (!diaconnG8Pump.bolusStopped) {
                SystemClock.sleep(200)
            }
        } else {
            diaconnG8Pump.bolusStopped = true
        }
    }

    fun tempBasal(absoluteRate: Double, durationInHours: Double): Boolean {
        if (!isConnected) return false

        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        val result: DiaconnG8Packet
        if (diaconnG8Pump.isPumpVersionGe3_53) {
            val tbrPacket = TempBasalSettingPacket(injector, 3, ((durationInHours * 60) / 15).toInt(), ((absoluteRate * 100) + 1000).toInt())
            sendMessage(tbrPacket, 100)
            result = tbrPacket
            if (!processConfirm(tbrPacket.msgType)) return false
        } else {

            if (diaconnG8Pump.tbStatus == 1) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
                val tbrPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
                // tempbasal stop
                sendMessage(tbrPacket, 100)
                // otp process
                if (!processConfirm(tbrPacket.msgType)) return false
                diaconnG8Pump.tempBasalStart = dateUtil.now()
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
            val tbInjectRate = ((absoluteRate * 100) + 1000).toInt()
            val tbrPacket = TempBasalSettingPacket(injector, 1, ((durationInHours * 60) / 15).toInt(), tbInjectRate)
            sendMessage(tbrPacket, 100)
            result = tbrPacket
            // otp process
            if (!processConfirm(tbrPacket.msgType)) return false
            // pump tempbasal status inquire
        }

        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return result.success()
    }

    fun tempBasalShortDuration(absoluteRate: Double, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }

        // temp state check
        val result: DiaconnG8Packet
        sendMessage(TempBasalInquirePacket(injector))
        if (diaconnG8Pump.isPumpVersionGe3_53) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
            val tbrSettingPacket = TempBasalSettingPacket(injector, 3, 2, ((absoluteRate * 100) + 1000).toInt())
            sendMessage(tbrSettingPacket, 100)
            result = tbrSettingPacket
            // otp process
            if (!processConfirm(tbrSettingPacket.msgType)) return false
            sendMessage(TempBasalInquirePacket(injector))
        } else {
            if (diaconnG8Pump.tbStatus == 1) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
                val tbrPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
                // tempbasal stop
                sendMessage(tbrPacket, 100)
                // otp process
                if (!processConfirm(tbrPacket.msgType)) return false
                SystemClock.sleep(500)
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
            val tbInjectRate = absoluteRate * 100 + 1000
            val msgTBR = TempBasalSettingPacket(injector, 1, 2, tbInjectRate.toInt())
            sendMessage(msgTBR, 100)
            result = msgTBR
            // otp process
            if (!processConfirm(msgTBR.msgType)) return false
        }
        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return result.success()
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
            if (!processConfirm(msgPacket.msgType)) return false
            SystemClock.sleep(500)
        }

        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun extendedBolus(insulin: Double, durationInMinutes: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        aapsLogger.debug(LTag.PUMPCOMM, "insulin: $insulin durationInMinutes: $durationInMinutes")

        val msgExtended = InjectionExtendedBolusSettingPacket(injector, (insulin * 100).toInt(), durationInMinutes, dateUtil.now())
        sendMessage(msgExtended)
        // otp process
        if (!processConfirm(msgExtended.msgType)) return false
        //diaconnG8Pump.isExtendedInProgress = true
        loadHistory()
        val eb = pumpSync.expectedPumpState().extendedBolus
        diaconnG8Pump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgExtended.success()
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        val msgType = if (diaconnG8Pump.dualStatus == 1) 0x09.toByte() else 0x08.toByte()
        val msgStop = InjectionCancelSettingPacket(injector, msgType)
        sendMessage(msgStop)
        // otp process
        if (!processConfirm(msgStop.msgType)) return false
        loadHistory() // pump log sync( db update)
        val eb = pumpSync.expectedPumpState().extendedBolus
        diaconnG8Pump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgStop.success()
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
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
        if (!processConfirm(requestReqPacket4.msgType)) return false
        // pump saving time about 30 second
        aapsLogger.debug(LTag.PUMPCOMM, "30 seconds Waiting!!")
        SystemClock.sleep(30000)

        val msgPacket = InjectionBasalSettingPacket(injector, 1)
        sendMessage(msgPacket)
        // otp process
        if (!processConfirm(msgPacket.msgType)) return false
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return requestReqPacket4.success()
    }

    private fun processConfirm(msgType: Byte): Boolean {
        // pump confirm
        var loopCnt = 0
        // waiting 2 seconds for otp
        while (loopCnt < 20) {
            if (diaconnG8Pump.otpNumber == 0) {
                SystemClock.sleep(100)
                aapsLogger.error(LTag.PUMPCOMM, "OTP waiting 100ms $loopCnt / 20")
            }
            loopCnt++
        }
        // after 2 second
        if (diaconnG8Pump.otpNumber == 0) {
            aapsLogger.error(LTag.PUMPCOMM, "otp is not received yet")
            return false
        }
        diaconnG8Pump.bolusConfirmMessage = msgType
        sendMessage(AppConfirmSettingPacket(injector, msgType, diaconnG8Pump.otpNumber), 2000)
        diaconnG8Pump.otpNumber = 0
        return true
    }
}