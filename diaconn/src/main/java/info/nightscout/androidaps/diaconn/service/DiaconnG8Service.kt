package info.nightscout.androidaps.diaconn.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.diaconn.DiaconnG8Plugin
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.events.EventDiaconnG8NewStatus
import info.nightscout.androidaps.diaconn.packet.*
import info.nightscout.androidaps.diaconn.pumplog.PumplogUtil
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.StringUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

class DiaconnG8Service : DaggerService() {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var context: Context
    @Inject lateinit var diaconnG8Plugin : DiaconnG8Plugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var diaconnG8ResponseMessageHashTable: DiaconnG8ResponseMessageHashTable
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var bleCommonService: BLECommonService
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private var lastApproachingDailyLimit: Long = 0

    override fun onCreate() {
        super.onCreate()
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(Schedulers.io())
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
        bleCommonService.sendMessage(message, 500)
    }

    fun readPumpStatus() {
        try {
            val pump = activePlugin.activePump
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)))

            val pumpFirmwareVersion = sp.getString(resourceHelper.gs(R.string.pumpversion),"")

            if(!StringUtils.emptyString(pumpFirmwareVersion) && PumplogUtil.isPumpVersionGe(pumpFirmwareVersion, 3, 0)) {
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
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)))

                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }

            // 시간 설정
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumptime)))
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
            if (abs(timeDiff) > 60) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    ErrorHelperActivity.runAlarm(context, resourceHelper.gs(R.string.largetimediff), resourceHelper.gs(R.string.largetimedifftitle), R.raw.error)


                    //de-initialize pump
                    diaconnG8Pump.reset()
                    rxBus.send(EventDiaconnG8NewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {

                    if(!diaconnG8Pump.isTempBasalInProgress) {
                        val msgPacket = TimeSettingPacket(injector, dateUtil.now(), offset)
                        sendMessage(msgPacket)

                        // otp process
                        if(!processConfirm(msgPacket.msgType)) return

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
                    val reportFail = Notification(Notification.APPROACHING_DAILY_LIMIT, resourceHelper.gs(R.string.approachingdailylimit), Notification.URGENT)
                    rxBus.send(EventNewNotification(reportFail))
                    pumpSync.insertAnnouncement(resourceHelper.gs(R.string.approachingdailylimit) + ": " + diaconnG8Pump.dailyTotalUnits + "/" + diaconnG8Pump.maxDailyTotalUnits + "U", null, PumpType.DIACONN_G8, diaconnG8Pump.serialNo.toString())
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
        if(diaconnG8Pump.isPumpVersionGe2_63) {
            sendMessage(IncarnationInquirePacket(injector))
        }

        val result = PumpEnactResult(injector)

        // pump log status
        val pumpLastNum = diaconnG8Pump.pumpLastLogNum
        val pumpWrappingCount = diaconnG8Pump.pumpWrappingCount
        val apsIncarnationNum = sp.getInt(resourceHelper.gs(R.string.apsIncarnationNo), 65536)
        // aps last log num
        val pumpSerialNo      = sp.getInt(resourceHelper.gs(R.string.pumpserialno), 0)
        val apsWrappingCount  = sp.getInt(resourceHelper.gs(R.string.apsWrappingCount), 0)
        val apsLastLogNum     = sp.getInt(resourceHelper.gs(R.string.apslastLogNum), 0)

        // if first install app
        if(apsWrappingCount == 0 && apsLastLogNum == 0 ) {
            pumpLogDefaultSetting()
        }

        // if pump reset
        if(apsIncarnationNum != diaconnG8Pump.pumpIncarnationNum) {
            pumpLogDefaultSetting()
            sp.putInt(resourceHelper.gs(R.string.apsIncarnationNo), diaconnG8Pump.pumpIncarnationNum)
        }

        // if another pump
        if(pumpSerialNo != diaconnG8Pump.serialNo) {
            pumpLogDefaultSetting()
            sp.putInt(resourceHelper.gs(R.string.pumpserialno), diaconnG8Pump.serialNo)
        }

        val apsLastNum = apsWrappingCount * 10000 + apsLastLogNum
        if((pumpWrappingCount * 10000 + pumpLastNum) < apsLastLogNum ) {
            pumpLogDefaultSetting()
        }

        val start:Int? // log sync startNo
        val end:Int? // log sync endNo
        if (((pumpWrappingCount * 10000 + pumpLastNum) - apsLastNum) > 10000) {
            start = pumpLastNum
            end = 10000
        } else if (pumpWrappingCount > apsWrappingCount && apsLastLogNum < 9999) {
            start = apsLastLogNum + 1
            end = 10000
        } else if (pumpWrappingCount > apsWrappingCount && apsLastLogNum >= 9999) {
            start = 0
            end = pumpLastNum
        } else {
            start = apsLastLogNum + 1
            end = pumpLastNum
        }

        // pump log loop size
        val pumpLogPageSize = 11
        val loopCount: Int = ceil (((end - start) / 11.0)).toInt()

        // log sync start!
        if (loopCount > 0) {
            diaconnG8Pump.isProgressPumpLogSync = true

            for (i in 0 until loopCount) {
                val startLogNo: Int = start + i * pumpLogPageSize
                val endLogNo: Int = startLogNo + min(end - startLogNo, pumpLogPageSize)
                val msg = BigLogInquirePacket(injector, startLogNo, endLogNo, 100)
                sendMessage(msg)
            }
            diaconnG8Pump.historyDoneReceived = true
            while (!diaconnG8Pump.historyDoneReceived && bleCommonService.isConnected) {
                SystemClock.sleep(100)
            }
            result.success(true)
            diaconnG8Pump.lastConnection = System.currentTimeMillis()
        }
        return result
    }

    fun setUserSettings(): PumpEnactResult {
        val result = PumpEnactResult(injector)

        val msg: DiaconnG8Packet = when(diaconnG8Pump.setUserOptionType) {
            DiaconnG8Pump.ALARM -> SoundSettingPacket(injector, diaconnG8Pump.beepAndAlarm, diaconnG8Pump.alarmIntesity)
            DiaconnG8Pump.LCD -> DisplayTimeoutSettingPacket(injector, diaconnG8Pump.lcdOnTimeSec)
            DiaconnG8Pump.LANG -> LanguageSettingPacket(injector, diaconnG8Pump.selectedLanguage)
            DiaconnG8Pump.BOLUS_SPEED -> BolusSpeedSettingPacket(injector, diaconnG8Pump.bolusSpeed)
            else -> null
        } ?: return result.success(false)

        sendMessage(msg )
        // pump confirm
        if(diaconnG8Pump.otpNumber == 0) {
            aapsLogger.error(LTag.PUMPCOMM, "otp is not received yet")
            result.success(false)
            result.comment("펌프와 연결 상태를 확인해주세요.")
            return result
        }
        sendMessage(AppConfirmSettingPacket(injector, msg.msgType, diaconnG8Pump.otpNumber))
        diaconnG8Pump.otpNumber = 0
        SystemClock.sleep(100)
        return result.success(true)
    }

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        if (BolusProgressDialog.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.startingbolus)))

        // bolus speed setting
        val apsPrefBolusSpeed = sp.getInt("g8_bolusspeed", 5)
        val isSpeedSyncToPump = sp.getBoolean("diaconn_g8_isbolusspeedsync", false)

        // aps speed check
        if(!isSpeedSyncToPump) {
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
                sendMessage(start)
                // otp process
                if(!processConfirm(start.msgType)) return false
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
        val expectedEnd = bolusStart + bolusDurationInMSec + 7500L
        val totalwaitTime = (expectedEnd - System.currentTimeMillis()) /1000
        while (!diaconnG8Pump.bolusDone) {
            val waitTime = (expectedEnd - System.currentTimeMillis()) / 1000
            bolusingEvent.status = String.format(resourceHelper.gs(R.string.waitingforestimatedbolusend), if(waitTime < 0) 0 else waitTime)
            var progressPecent = 0
            if(totalwaitTime > waitTime) {
                progressPecent = ((totalwaitTime - waitTime) * 100 / totalwaitTime).toInt()
            }
            bolusingEvent.percent = min(progressPecent, 100)
            rxBus.send(bolusingEvent)
            SystemClock.sleep(200)
        }

        // do not call loadHistory() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // reread bolus status
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)))
                sendMessage(InjectionSnackInquirePacket(injector), 1000) // last bolus
                // 볼러스 결과 보고패킷에서 처리함.
                bolusingEvent.percent = 100
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.disconnecting)))
            }
        })
       return !start.failed
    }

    fun bolusStop() {
        val stop = InjectionCancelSettingPacket(injector, 0x07.toByte())
        diaconnG8Pump.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop)
            // otp process
            if(!processConfirm(stop.msgType)) return
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

        if (diaconnG8Pump.tbStatus ==1 ) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            val msgPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
            // tempbasal stop
            sendMessage(msgPacket)
            // otp process
            if(!processConfirm(msgPacket.msgType)) return false
            diaconnG8Pump.tempBasalStart= dateUtil.now()
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))
        val tbInjectRate = ((absoluteRate*100) + 1000).toInt()
        val msgTBR = TempBasalSettingPacket(injector, 1, ((durationInHours * 60) / 15).toInt(), tbInjectRate)
        sendMessage(msgTBR)
        // otp process
        if(!processConfirm(msgTBR.msgType)) return false
        // pump tempbasal status inquire
        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun highTempBasal(absoluteRate: Double): Boolean {
        // temp state check
        sendMessage(TempBasalInquirePacket(injector))

        if (diaconnG8Pump.tbStatus ==1 ) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            val msgPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
            // tempbasal stop
            sendMessage(msgPacket)
            // otp process
            if(!processConfirm(msgPacket.msgType)) return false
            diaconnG8Pump.tempBasalStart= dateUtil.now()
            // SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))

        val tbTime = 2 // 2: 30min, 3:45min, 4:60min
        var newAbsoluteRate = absoluteRate
        if (absoluteRate < 0.0)  newAbsoluteRate = 0.0
        if (absoluteRate > 6.0) newAbsoluteRate = 6.0 // pump Temp Max percent = 200

        aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start absoluteRate: $newAbsoluteRate duration 30 min")
        val tbInjectRate = absoluteRate * 100 + 1000
        val msgTBR = TempBasalSettingPacket(injector, 1, tbTime, tbInjectRate.toInt())
        sendMessage(msgTBR)
        // otp process
        if(!processConfirm(msgTBR.msgType)) return false
        sendMessage(TempBasalInquirePacket(injector))
        SystemClock.sleep(500)
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun tempBasalShortDuration(absoluteRate: Double, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }

        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        if (diaconnG8Pump.tbStatus ==1 ) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            val msgPacket = TempBasalSettingPacket(injector, 2, diaconnG8Pump.tbTime, diaconnG8Pump.tbInjectRateRatio)
            // tempbasal stop
            sendMessage(msgPacket)
            // otp process
            if(!processConfirm(msgPacket.msgType)) return false
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))
        val tbInjectRate = absoluteRate * 100 + 1000
        val msgTBR = TempBasalSettingPacket(injector, 1, 2, tbInjectRate.toInt())
        sendMessage(msgTBR)
        // otp process
        if(!processConfirm(msgTBR.msgType)) return false
        sendMessage(TempBasalInquirePacket(injector))
        loadHistory()
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        diaconnG8Pump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
        // temp state check
        sendMessage(TempBasalInquirePacket(injector))
        if(diaconnG8Pump.tbStatus == 1) {
            val msgPacket = TempBasalSettingPacket(
                injector,
                2,
                diaconnG8Pump.tbTime,
                diaconnG8Pump.tbInjectRateRatio
            )
            // tempbasal stop
            sendMessage(msgPacket)
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
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingextendedbolus)))
        aapsLogger.error(LTag.PUMPCOMM, "insulin: $insulin durationInMinutes: $durationInMinutes")

        val msgExtended = InjectionExtendedBolusSettingPacket(injector, (insulin * 100).toInt(), durationInMinutes, dateUtil.now())
        sendMessage(msgExtended)
        // otp process
        if(!processConfirm(msgExtended.msgType)) return false
        //diaconnG8Pump.isExtendedInProgress = true
        loadHistory()
        val eb = pumpSync.expectedPumpState().extendedBolus
        diaconnG8Pump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgExtended.success()
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingextendedbolus)))
        val msgType  = if(diaconnG8Pump.dualStatus == 1) 0x09.toByte() else 0x08.toByte()
        val msgStop = InjectionCancelSettingPacket(injector, msgType)
        sendMessage(msgStop)
        // otp process
        if(!processConfirm(msgStop.msgType)) return false
        loadHistory() // pump log sync( db update)
        val eb = pumpSync.expectedPumpState().extendedBolus
        diaconnG8Pump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return  msgStop.success()
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.updatingbasalrates)))
        val basalList = diaconnG8Pump.buildDiaconnG8ProfileRecord(profile)

        val requestReqPacket1 = BasalSettingPacket(
            injector, 1, 1, (basalList[0] * 100).toInt(), (basalList[1] * 100).toInt(), (basalList[2] * 100).toInt(), (basalList[3] * 100).toInt(), (basalList[4] * 100).toInt(), (basalList[5] * 100).toInt()
        )
        val requestReqPacket2= BasalSettingPacket(
            injector, 1, 2, (basalList[6] * 100).toInt(), (basalList[7] * 100).toInt(), (basalList[8] * 100).toInt(), (basalList[9] * 100).toInt(), (basalList[10] * 100).toInt(), (basalList[11] * 100).toInt()
        )
        val requestReqPacket3= BasalSettingPacket(
            injector, 1, 3, (basalList[12] * 100).toInt(), (basalList[13] * 100).toInt(), (basalList[14] * 100).toInt(), (basalList[15] * 100).toInt(), (basalList[16] * 100).toInt(), (basalList[17] * 100).toInt()
        )
        val requestReqPacket4= BasalSettingPacket(
            injector, 1, 4, (basalList[18] * 100).toInt(), (basalList[19] * 100).toInt(), (basalList[20] * 100).toInt(), (basalList[21] * 100).toInt(), (basalList[22] * 100).toInt(), (basalList[23] * 100).toInt()
        )
        // setting basal pattern 1,2,3,4
        sendMessage(requestReqPacket1)
        sendMessage(requestReqPacket2)
        sendMessage(requestReqPacket3)
        sendMessage(requestReqPacket4)

        // otp process
        if(!processConfirm(requestReqPacket4.msgType)) return false
        // pump saving time about 30 second
        aapsLogger.debug(LTag.PUMPCOMM, "30 seconds Waiting!!")
        SystemClock.sleep(30000)

        val msgPacket = InjectionBasalSettingPacket(injector, 1)
        sendMessage(msgPacket)
        // otp process
        if(!processConfirm(msgPacket.msgType)) return false
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return requestReqPacket4.success()
    }

    private fun pumpLogDefaultSetting() {
        val apsWrappingCount = diaconnG8Pump.pumpWrappingCount
        val apsLastLogNum = if(diaconnG8Pump.pumpLastLogNum - 1 < 0 ) 0 else  diaconnG8Pump.pumpLastLogNum - 1
        sp.putInt(resourceHelper.gs(R.string.apslastLogNum), apsLastLogNum)
        sp.putInt(resourceHelper.gs(R.string.apsWrappingCount), apsWrappingCount)
    }

    private fun processConfirm(msgType:Byte) : Boolean {
        // pump confirm
        if(diaconnG8Pump.otpNumber == 0) {
            aapsLogger.error(LTag.PUMPCOMM, "otp is not received yet")

            // Comments are made as dialogs are exposed twice each in the event of an error.
            // Thread {
            //     val i = Intent(context, ErrorHelperActivity::class.java)
            //     i.putExtra("soundid", R.raw.boluserror)
            //     i.putExtra("status", resourceHelper.gs(R.string.diaconn_g8_errotpreceivedyet))
            //     i.putExtra("title", resourceHelper.gs(R.string.pumperror))
            //     i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //     context.startActivity(i)
            // }.start()

            return false
        }
        sendMessage(AppConfirmSettingPacket(injector, msgType, diaconnG8Pump.otpNumber), 2000)
        diaconnG8Pump.otpNumber = 0
        return true
    }
}