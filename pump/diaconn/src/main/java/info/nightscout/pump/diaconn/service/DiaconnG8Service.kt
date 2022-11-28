package info.nightscout.pump.diaconn.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.BolusProgressData
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.diaconn.DiaconnG8Plugin
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.pump.diaconn.R
import info.nightscout.pump.diaconn.api.DiaconnApiService
import info.nightscout.pump.diaconn.api.DiaconnLogUploader
import info.nightscout.pump.diaconn.database.DiaconnHistoryRecordDao
import info.nightscout.pump.diaconn.events.EventDiaconnG8NewStatus
import info.nightscout.pump.diaconn.packet.AppConfirmSettingPacket
import info.nightscout.pump.diaconn.packet.BasalLimitInquirePacket
import info.nightscout.pump.diaconn.packet.BasalSettingPacket
import info.nightscout.pump.diaconn.packet.BigAPSMainInfoInquirePacket
import info.nightscout.pump.diaconn.packet.BigLogInquirePacket
import info.nightscout.pump.diaconn.packet.BigMainInfoInquirePacket
import info.nightscout.pump.diaconn.packet.BolusSpeedInquirePacket
import info.nightscout.pump.diaconn.packet.BolusSpeedSettingPacket
import info.nightscout.pump.diaconn.packet.DiaconnG8Packet
import info.nightscout.pump.diaconn.packet.DisplayTimeInquirePacket
import info.nightscout.pump.diaconn.packet.DisplayTimeoutSettingPacket
import info.nightscout.pump.diaconn.packet.IncarnationInquirePacket
import info.nightscout.pump.diaconn.packet.InjectionBasalSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionCancelSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionExtendedBolusSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionSnackInquirePacket
import info.nightscout.pump.diaconn.packet.InjectionSnackSettingPacket
import info.nightscout.pump.diaconn.packet.LanguageInquirePacket
import info.nightscout.pump.diaconn.packet.LanguageSettingPacket
import info.nightscout.pump.diaconn.packet.LogStatusInquirePacket
import info.nightscout.pump.diaconn.packet.SerialNumInquirePacket
import info.nightscout.pump.diaconn.packet.SneckLimitInquirePacket
import info.nightscout.pump.diaconn.packet.SoundInquirePacket
import info.nightscout.pump.diaconn.packet.SoundSettingPacket
import info.nightscout.pump.diaconn.packet.TempBasalInquirePacket
import info.nightscout.pump.diaconn.packet.TempBasalSettingPacket
import info.nightscout.pump.diaconn.packet.TimeInquirePacket
import info.nightscout.pump.diaconn.packet.TimeSettingPacket
import info.nightscout.pump.diaconn.pumplog.PumplogUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventInitializationChanged
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class DiaconnG8Service : DaggerService() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
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

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private var lastApproachingDailyLimit: Long = 0

    override fun onCreate() {
        super.onCreate()
        disposable.add(rxBus
                           .toObservable(EventAppExit::class.java)
                           .observeOn(aapsSchedulers.io)
                           .subscribe({ stopSelf() }) { fabricPrivacy.logException(it) }
        )
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: DiaconnG8Service
            get() = this@DiaconnG8Service
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
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

            val pumpFirmwareVersion = sp.getString(rh.gs(R.string.pumpversion), "")

            if (pumpFirmwareVersion.isNotEmpty() && PumplogUtil.isPumpVersionGe(pumpFirmwareVersion, 3, 0)) {
                sendMessage(BigAPSMainInfoInquirePacket(injector)) // APS Pump Main Info
            } else {
                sendMessage(BasalLimitInquirePacket(injector)) // basal Limit
                sendMessage(SneckLimitInquirePacket(injector)) // bolus Limit
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
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), R.raw.error)

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
            //NSUpload.uploadDeviceStatus();
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
            val result = PumpEnactResult(injector).success(false)
            result.comment = "pump not initialized"
            return result
        }
        sendMessage(LogStatusInquirePacket(injector))
        // pump version check
        if (diaconnG8Pump.isPumpVersionGe2_63) {
            sendMessage(IncarnationInquirePacket(injector))
        }

        val result = PumpEnactResult(injector)
        var apsLastLogNum = 9999
        var apsWrappingCount = -1
        // get saved last loginfo
        val diaconnHistoryRecord = diaconnHistoryRecordDao.getLastRecord(diaconnG8Pump.pumpUid)
        aapsLogger.debug(LTag.PUMPCOMM, "diaconnHistoryRecord :: $diaconnHistoryRecord")

        if (diaconnHistoryRecord != null) {
            apsLastLogNum = diaconnHistoryRecord.lognum
            apsWrappingCount = diaconnHistoryRecord.wrappingCount
        }

        // pump log status
        val pumpLastNum = diaconnG8Pump.pumpLastLogNum
        val pumpWrappingCount = diaconnG8Pump.pumpWrappingCount
        val apsIncarnationNum = sp.getInt(rh.gs(R.string.apsIncarnationNo), 65536)
        // aps last log num
        val pumpSerialNo = sp.getInt(rh.gs(R.string.pumpserialno), 0)

        // if first install app
        if (apsWrappingCount == -1 && apsLastLogNum == 9999) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
        }
        // if another pump
        if (pumpSerialNo != diaconnG8Pump.serialNo) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
            sp.putInt(rh.gs(R.string.pumpserialno), diaconnG8Pump.serialNo)
        }
        // if pump reset
        if (apsIncarnationNum != diaconnG8Pump.pumpIncarnationNum) {
            apsWrappingCount = pumpWrappingCount
            apsLastLogNum = if (pumpLastNum - 1 < 0) 0 else pumpLastNum - 2
            sp.putInt(R.string.apsIncarnationNo, apsIncarnationNum)
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
                val msg = BigLogInquirePacket(injector, startLogNo, endLogNo, 100)
                sendMessage(msg, 500)
            }
            result.success(true)
            diaconnG8Pump.lastConnection = System.currentTimeMillis()
        }
        // upload pump log to Diaconn Cloud
        if (sp.getBoolean(R.string.key_diaconn_g8_cloudsend, true)) {
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

        if (pumpWrappingCount > wrappingCount && lastLogNum < 9999) {
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
        val result = PumpEnactResult(injector)

        val msg: DiaconnG8Packet = when (diaconnG8Pump.setUserOptionType) {
            DiaconnG8Pump.ALARM -> SoundSettingPacket(injector, diaconnG8Pump.beepAndAlarm, diaconnG8Pump.alarmIntesity)
            DiaconnG8Pump.LCD -> DisplayTimeoutSettingPacket(injector, diaconnG8Pump.lcdOnTimeSec)
            DiaconnG8Pump.LANG -> LanguageSettingPacket(injector, diaconnG8Pump.selectedLanguage)
            DiaconnG8Pump.BOLUS_SPEED -> BolusSpeedSettingPacket(injector, diaconnG8Pump.bolusSpeed)
            else -> null
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

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        if (BolusProgressData.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.startingbolus)))

        // bolus speed setting
        val apsPrefBolusSpeed = sp.getInt(R.string.key_diaconn_g8_bolusspeed, 5)
        val isSpeedSyncToPump = sp.getBoolean(R.string.key_diaconn_g8_is_bolus_speed_sync, false)

        // aps speed check
        if (!isSpeedSyncToPump) {
            val msg = BolusSpeedSettingPacket(injector, apsPrefBolusSpeed)
            sendMessage(msg)
            sendMessage(AppConfirmSettingPacket(injector, msg.msgType, diaconnG8Pump.otpNumber))
            diaconnG8Pump.otpNumber = 0
        }

        // pump bolus speed inquire
        sendMessage(BolusSpeedInquirePacket(injector))
        diaconnG8Pump.bolusDone = false
        diaconnG8Pump.bolusingTreatment = t
        diaconnG8Pump.bolusAmountToBeDelivered = insulin
        diaconnG8Pump.bolusStopped = false
        diaconnG8Pump.bolusStopForced = false
        diaconnG8Pump.bolusProgressLastTimeStamp = dateUtil.now()
        val start = InjectionSnackSettingPacket(injector, (insulin * 100).toInt())
        if (carbs > 0) {
            pumpSync.syncCarbsWithTimestamp(carbTime, carbs.toDouble(), null, PumpType.DIACONN_G8, diaconnG8Pump.serialNo.toString())
        }
        val bolusStart = System.currentTimeMillis()
        if (insulin > 0) {
            if (!diaconnG8Pump.bolusStopped) {
                sendMessage(start, 100)
                // otp process
                if (!processConfirm(start.msgType)) return false
            } else {
                t.insulin = 0.0
                return false
            }
        }
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.t = t
        bolusingEvent.percent = 99
        //diaconnG8Pump.bolusingTreatment = null
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
        if (diaconnG8Pump.isReadyToBolus) {
            while (!diaconnG8Pump.bolusDone) {
                val waitTime = (expectedEnd - System.currentTimeMillis()) / 1000
                bolusingEvent.status = String.format(rh.gs(R.string.waitingforestimatedbolusend), if (waitTime < 0) 0 else waitTime)
                var progressPecent = 0
                if (totalwaitTime > waitTime) {
                    progressPecent = ((totalwaitTime - waitTime) * 100 / totalwaitTime).toInt()
                }
                bolusingEvent.percent = min(progressPecent, 100)
                rxBus.send(bolusingEvent)
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
                // 볼러스 결과 보고패킷에서 처리함.
                bolusingEvent.percent = 100
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.disconnecting)))
            }
        })
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

        if (diaconnG8Pump.tbStatus == 1) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            val msgPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
            // tempbasal stop
            sendMessage(msgPacket, 100)
            // otp process
            if (!processConfirm(msgPacket.msgType)) return false
            diaconnG8Pump.tempBasalStart = dateUtil.now()
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        val tbInjectRate = ((absoluteRate * 100) + 1000).toInt()
        val msgTBR = TempBasalSettingPacket(injector, 1, ((durationInHours * 60) / 15).toInt(), tbInjectRate)
        sendMessage(msgTBR, 100)
        // otp process
        if (!processConfirm(msgTBR.msgType)) return false
        // pump tempbasal status inquire
        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun tempBasalShortDuration(absoluteRate: Double, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }

        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        if (diaconnG8Pump.tbStatus == 1) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            val msgPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
            // tempbasal stop
            sendMessage(msgPacket, 100)
            // otp process
            if (!processConfirm(msgPacket.msgType)) return false
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        val tbInjectRate = absoluteRate * 100 + 1000
        val msgTBR = TempBasalSettingPacket(injector, 1, 2, tbInjectRate.toInt())
        sendMessage(msgTBR, 100)
        // otp process
        if (!processConfirm(msgTBR.msgType)) return false
        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
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