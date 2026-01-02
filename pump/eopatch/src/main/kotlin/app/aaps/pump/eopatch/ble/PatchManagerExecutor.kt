package app.aaps.pump.eopatch.ble

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.EoPatchRxBus.listen
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.ble.task.ActivateTask
import app.aaps.pump.eopatch.ble.task.DeactivateTask
import app.aaps.pump.eopatch.ble.task.GetPatchInfoTask
import app.aaps.pump.eopatch.ble.task.InfoReminderTask
import app.aaps.pump.eopatch.ble.task.NeedleSensingTask
import app.aaps.pump.eopatch.ble.task.PauseBasalTask
import app.aaps.pump.eopatch.ble.task.PrimingTask
import app.aaps.pump.eopatch.ble.task.ResumeBasalTask
import app.aaps.pump.eopatch.ble.task.SelfTestTask
import app.aaps.pump.eopatch.ble.task.SetLowReservoirTask
import app.aaps.pump.eopatch.ble.task.StartBondTask
import app.aaps.pump.eopatch.ble.task.StartCalcBolusTask
import app.aaps.pump.eopatch.ble.task.StartNormalBasalTask
import app.aaps.pump.eopatch.ble.task.StartQuickBolusTask
import app.aaps.pump.eopatch.ble.task.StartTempBasalTask
import app.aaps.pump.eopatch.ble.task.StopComboBolusTask
import app.aaps.pump.eopatch.ble.task.StopExtBolusTask
import app.aaps.pump.eopatch.ble.task.StopNowBolusTask
import app.aaps.pump.eopatch.ble.task.StopTempBasalTask
import app.aaps.pump.eopatch.ble.task.TaskBase.Companion.enqueue
import app.aaps.pump.eopatch.ble.task.TaskFunc
import app.aaps.pump.eopatch.ble.task.UpdateConnectionTask
import app.aaps.pump.eopatch.code.BolusExDuration
import app.aaps.pump.eopatch.code.DeactivationStatus
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.core.Patch
import app.aaps.pump.eopatch.core.api.BuzzerStop
import app.aaps.pump.eopatch.core.api.GetTemperature
import app.aaps.pump.eopatch.core.api.PublicKeySend
import app.aaps.pump.eopatch.core.api.SequenceGet
import app.aaps.pump.eopatch.core.api.StopAeBeep
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.noti.AlarmNotification
import app.aaps.pump.eopatch.core.noti.BaseNotification
import app.aaps.pump.eopatch.core.noti.InfoNotification
import app.aaps.pump.eopatch.core.response.BasalScheduleSetResponse
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.response.BolusResponse
import app.aaps.pump.eopatch.core.response.BolusStopResponse
import app.aaps.pump.eopatch.core.response.ComboBolusStopResponse
import app.aaps.pump.eopatch.core.response.KeyResponse
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.response.TempBasalScheduleSetResponse
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.core.scan.IBleDevice
import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult
import app.aaps.pump.eopatch.event.EventEoPatchAlarm
import app.aaps.pump.eopatch.keys.EopatchBooleanKey
import app.aaps.pump.eopatch.keys.EopatchIntKey
import app.aaps.pump.eopatch.ui.receiver.RxBroadcastReceiver
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.NormalBasal
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.PatchState.Companion.create
import app.aaps.pump.eopatch.vo.TempBasal
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import java.lang.Byte
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class PatchManagerExecutor @Inject constructor(
    private val pm: PreferenceManager,
    private val patchConfig: PatchConfig,
    private val normalBasalManager: NormalBasalManager,
    private val alarms: Alarms,
    private val context: Context,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val START_BOND: StartBondTask,
    private val GET_PATCH_INFO: GetPatchInfoTask,
    private val SELF_TEST: SelfTestTask,
    private val START_PRIMING: PrimingTask,
    private val START_NEEDLE_CHECK: NeedleSensingTask
) {

    var patch: IBleDevice = Patch.getInstance()

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    private val BUZZER_STOP: BuzzerStop = BuzzerStop()
    private val TEMPERATURE_GET: GetTemperature = GetTemperature()
    private val ALARM_ALERT_ERROR_BEEP_STOP: StopAeBeep = StopAeBeep()
    private val PUBLIC_KEY_SET: PublicKeySend = PublicKeySend()
    private val SEQUENCE_GET: SequenceGet = SequenceGet()

    @Inject fun onInit() {
        patch.init(context)
        patch.setSeq(patchConfig.seq15)

        val filter = IntentFilter(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_DATE_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)

        val dateTimeChanged: Observable<Intent> = RxBroadcastReceiver.Companion.create(context, filter)

        compositeDisposable.add(
            Observable.combineLatest<Boolean, PatchLifecycle, Boolean>(patch.observeConnected(), pm.observePatchLifeCycle(),
                                                                       BiFunction { connected: Boolean, lifeCycle: PatchLifecycle -> (connected && lifeCycle.isActivated) })
                .subscribeOn(aapsSchedulers.io)
                .filter(Predicate { ok: Boolean -> ok })
                .observeOn(aapsSchedulers.io)
                .doOnNext(Consumer { enqueue(TaskFunc.UPDATE_CONNECTION) })
                .retry()
                .subscribe()
        )

        compositeDisposable.add(
            Observable.combineLatest<Boolean, PatchLifecycle, Intent, Boolean>(patch.observeConnected(),
                                                                               pm.observePatchLifeCycle().distinctUntilChanged(),
                                                                               dateTimeChanged.startWith(Observable.just<Intent>(Intent())),
                                                                               Function3 { connected: Boolean, lifeCycle: PatchLifecycle, value: Intent -> (connected && lifeCycle.isActivated) })
                .subscribeOn(aapsSchedulers.io)
                .doOnNext(Consumer { v: Boolean -> aapsLogger.debug(LTag.PUMP, "Has the date or time changed $v") })
                .filter(Predicate { ok: Boolean -> ok })
                .doOnNext(Consumer { enqueue(TaskFunc.SET_GLOBAL_TIME) })
                .doOnError(Consumer { aapsLogger.error(LTag.PUMP, "Failed to set EOPatch time.") })
                .retry()
                .subscribe()
        )

        compositeDisposable.add(
            patch.observeConnected()
                .doOnNext(Consumer { connected: Boolean -> this.onPatchConnected(connected) })
                .subscribe()
        )

        compositeDisposable.add(
            patchConfig.observe().doOnNext(Consumer { config: PatchConfig ->
                val newKey = config.sharedKey
                patch.updateEncryptionParam(newKey)
            }).subscribe()
        )

        compositeDisposable.add(
            listen<EventEoPatchAlarm>(EventEoPatchAlarm::class.java)
                .filter(EventEoPatchAlarm::isFirst)
                .filter(Predicate { !patchConfig.isDeactivated })
                .filter(Predicate { patch.connectionState.isConnected })
                .concatMapIterable<AlarmCode>(EventEoPatchAlarm::alarmCodes)
                .filter(AlarmCode::isPatchOccurrenceAlert)
                .subscribe()
        )

        compositeDisposable.add(
            listen<EventEoPatchAlarm>(EventEoPatchAlarm::class.java)
                .filter(EventEoPatchAlarm::isFirst)
                .filter(Predicate { !patchConfig.isDeactivated })
                .filter(Predicate { patch.connectionState.isConnected })
                .concatMapIterable<AlarmCode>(EventEoPatchAlarm::alarmCodes)
                .filter(AlarmCode::isPatchOccurrenceAlarm)
                .flatMap(Function { it: AlarmCode -> pauseBasalImpl(0.0f, System.currentTimeMillis(), it).toObservable() })
                .subscribe()
        )


        monitorPatchNotification()
    }

    private fun onPatchConnected(connected: Boolean) {
        val activated = patchConfig.isActivated
        val useEncryption = patchConfig.sharedKey != null
        val doseUnit = preferences.get(EopatchIntKey.LowReservoirReminder)
        val hours = preferences.get(EopatchIntKey.ExpirationReminder)
        val buzzer = preferences.get(EopatchBooleanKey.BuzzerReminder)
        val pc: PatchConfig = patchConfig

        if (connected && activated && useEncryption) {
            compositeDisposable.add(
                SEQUENCE_GET.get()
                    .map<Int>(Function { obj: KeyResponse -> obj.sequence })
                    .doOnSuccess(Consumer { sequence: Int ->
                        if (sequence >= 0) {
                            saveSequence(sequence)
                        }
                    })
                    .flatMap<Boolean>(Function {
                        if (pc.lowReservoirAlertAmount != doseUnit || pc.patchExpireAlertTime != hours) {
                            setLowReservoir(doseUnit, hours)
                                .doOnSuccess(Consumer {
                                    pc.lowReservoirAlertAmount = doseUnit
                                    pc.patchExpireAlertTime = hours
                                    pm.flushPatchConfig()
                                }).map<Boolean>(Function { true })
                        } else
                            Single.just<Boolean>(true)
                    })
                    .flatMap<Boolean>(Function {
                        if (pc.infoReminder != buzzer) {
                            infoReminderSet(buzzer)
                                .doOnSuccess(Consumer {
                                    pc.infoReminder = buzzer
                                    pm.flushPatchConfig()
                                }).map<Boolean>(Function { true })
                        } else
                            Single.just<Boolean>(true)
                    })
                    .flatMap<Any>(Function {
                        if (!alarms.needToStopBeep.isEmpty()) {
                            Observable.fromStream<AlarmCode>(alarms.needToStopBeep.stream())
                                .flatMapSingle<PatchBooleanResponse>(Function { alarmCode: AlarmCode ->
                                    stopAeBeep(alarmCode.aeCode).doOnSuccess(Consumer {
                                        alarms.needToStopBeep.remove(alarmCode)
                                    })
                                })
                                .lastOrError()
                        } else
                            Single.just<Boolean>(true)
                    })
                    .subscribe()
            )
        }

        if (!connected && activated) {
            patchConfig.updatetDisconnectedTime()
        }
    }

    private fun monitorPatchNotification() {
        compositeDisposable.addAll(
            patch.observeAlarmNotification()
                .subscribe(
                    Consumer { notification: AlarmNotification -> this.onAlarmNotification(notification) },
                    Consumer { throwable: Throwable -> aapsLogger.error(LTag.PUMP, throwable.message ?: "AlarmNotification observation error") }
                ),
            patch.observeInfoNotification()
                .filter(Predicate { patchConfig.isActivated })
                .subscribe(
                    Consumer { notification: InfoNotification -> this.onInfoNotification(notification) },
                    Consumer { throwable: Throwable -> aapsLogger.error(LTag.PUMP, throwable.message ?: "InfoNotification observation error") }
                )
        )
    }

    //==============================================================================================
    // preference database update helper
    //==============================================================================================
    // synchronized lock
    private val lock = Any()

    @Throws(Throwable::class) private fun updatePatchConfig(consumer: Consumer<PatchConfig>, needSave: Boolean) {
        synchronized(lock) {
            consumer.accept(patchConfig)
            if (needSave) {
                pm.flushPatchConfig()
            }
        }
    }

    @Synchronized fun updateBasal() {
        val normalBasal = normalBasalManager.normalBasal

        if (normalBasal.updateNormalBasalIndex()) {
            pm.flushNormalBasalManager()
        }
    }

    /**
     * getPatchConnection() 을 사용해야 한다.
     * 아직 Life Cycle 이 Activated 가 아님.
     *
     *
     * Activation Process task #1 Get Patch Information from Patch
     * Fragment: fragment_patch_connect_new
     */
    fun startBond(mac: String): Single<Boolean> {
        return START_BOND.start(mac)
    }

    fun getPatchInfo(timeout: Long): Single<Boolean> {
        return GET_PATCH_INFO.get().timeout(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Activation Process task #2 Check Patch is O.K
     * Fragment: fragment_patch_connect_new
     */
    fun selfTest(timeout: Long): Single<PatchSelfTestResult> {
        return SELF_TEST.start().timeout(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Activation Process task #3 PRIMING
     * Fragment: fragment_patch_priming
     */
    val temperature
        get() = TEMPERATURE_GET.get()
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)

    fun startPriming(timeout: Long, count: Long): Observable<Long> {
        return START_PRIMING.start(count)
            .timeout(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Activation Process task #4 NEEDLE SENSING
     * Fragment: fragment_patch_rotate_knob
     */
    fun checkNeedleSensing(timeout: Long): Single<Boolean> {
        return START_NEEDLE_CHECK.start()
            .timeout(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Activation Process task #5 Activation Secure Key, Basal writing
     * Fragment: fragment_patch_check_patch
     */
    @Inject lateinit var ACTIVATE: ActivateTask

    fun patchActivation(timeout: Long): Single<Boolean> {
        return ACTIVATE.start().timeout(timeout, TimeUnit.MILLISECONDS)
            .flatMap<Boolean>(Function { sharedKey() })
            .flatMap<Boolean>(Function { getSequence() })
            .doOnSuccess(Consumer { success: Boolean ->
                if (success) {
                    enqueue(TaskFunc.LOW_RESERVOIR)
                    enqueue(TaskFunc.INFO_REMINDER)
                }
            })
    }

    //==============================================================================================
    // IPatchManager interface [NORMAL BASAL]
    //==============================================================================================
    @Inject lateinit var startNormalBasalTask: StartNormalBasalTask

    fun startBasal(basal: NormalBasal): Single<BasalScheduleSetResponse> {
        return startNormalBasalTask.start(basal)
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    @Inject lateinit var resumeBasalTask: ResumeBasalTask

    fun resumeBasal(): Single<out BaseResponse> {
        return resumeBasalTask.resume()
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    fun pauseBasal(pauseDurationHour: Float): Single<out BaseResponse> {
        return pauseBasalImpl(pauseDurationHour, 0, null)
            .observeOn(SS)
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    //==============================================================================================
    // IPatchManager implementation [NORMAL BASAL]
    //==============================================================================================
    @Inject lateinit var pauseBasalTask: PauseBasalTask

    private fun pauseBasalImpl(pauseDurationHour: Float, alarmOccurredTime: Long, alarmCode: AlarmCode?): Single<out BaseResponse> {
        return pauseBasalTask.pause(pauseDurationHour, alarmOccurredTime, alarmCode)
    }

    //==============================================================================================
    // IPatchManager interface [TEMP BASAL]
    //==============================================================================================
    @Inject lateinit var startTempBasalTask: StartTempBasalTask

    fun startTempBasal(tempBasal: TempBasal): Single<TempBasalScheduleSetResponse> {
        return startTempBasalTask.start(tempBasal).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    // 템프베이젤 주입 정지
    // 템프베이젤이 정지되면 자동으로 노멀베이젤이 활성화된다
    // 외부에서 호출된다. 즉 명시적으로 tempBasal 정지. 이 때는 normalBasal resume 은 PatchState 보고 처리.
    @Inject lateinit var stopTempBasalTask: StopTempBasalTask

    fun stopTempBasal(): Single<PatchBooleanResponse> {
        return stopTempBasalTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    @Inject
    lateinit var startQuickBolusTask: StartQuickBolusTask

    @Inject
    lateinit var startCalcBolusTask: StartCalcBolusTask

    @Inject
    lateinit var stopComboBolusTask: StopComboBolusTask

    @Inject
    lateinit var stopNowBolusTask: StopNowBolusTask

    @Inject
    lateinit var stopExtBolusTask: StopExtBolusTask

    fun startQuickBolus(
        nowDoseU: Float, exDoseU: Float,
        exDuration: BolusExDuration
    ): Single<out BolusResponse> {
        return startQuickBolusTask.start(nowDoseU, exDoseU, exDuration)
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    fun startCalculatorBolus(detailedBolusInfo: DetailedBolusInfo): Single<out BolusResponse> {
        return startCalcBolusTask.start(detailedBolusInfo)
            .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    fun stopNowBolus(): Single<BolusStopResponse> {
        return stopNowBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    fun stopExtBolus(): Single<BolusStopResponse> {
        return stopExtBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    fun stopComboBolus(): Single<ComboBolusStopResponse> {
        return stopComboBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    //    private Single< extends BaseResponse> stopNowAndExtBolus() {
    //
    //        boolean nowActive = pm.getPatchState().isNowBolusActive();
    //        boolean extActive = pm.getPatchState().isExtBolusActive();
    //
    //        if (nowActive && extActive) {
    //            return stopComboBolus();
    //        } else if (nowActive) {
    //            return stopNowBolus();
    //        } else if (extActive) {
    //            return stopExtBolus();
    //        }
    //
    //        return Single.just(new PatchBooleanResponse(true));
    //    }
    //==============================================================================================
    // IPatchManager implementation [BOLUS]
    //==============================================================================================
    fun readBolusStatusFromNotification(infoNotification: InfoNotification) {
        if (infoNotification.isBolusRegAct) {
            val bolusCurrent = pm.bolusCurrent

            Arrays.asList<BolusType>(BolusType.NOW, BolusType.EXT).forEach(java.util.function.Consumer { type: BolusType ->
                if (infoNotification.isBolusRegAct(type)) { // 완료되었어도 업데이트 필요.
                    val injectedPumpCount = infoNotification.getInjected(type)
                    val remainPumpCount = infoNotification.getRemain(type)
                    bolusCurrent.updateBolusFromPatch(type, injectedPumpCount, remainPumpCount)
                }
            })
            pm.flushBolusCurrent()
        }
    }

    @Inject
    lateinit var deactivateTask: DeactivateTask

    // Patch Activation Tasks
    fun deactivate(timeout: Long, force: Boolean): Single<DeactivationStatus> {
        return deactivateTask.run(force, timeout)
    }

    @Inject
    lateinit var infoReminderTask: InfoReminderTask

    fun infoReminderSet(infoReminder: Boolean): Single<PatchBooleanResponse> {
        return infoReminderTask.set(infoReminder).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    @Inject
    lateinit var setLowReservoirTask: SetLowReservoirTask

    fun setLowReservoir(doseUnit: Int, hours: Int): Single<PatchBooleanResponse> {
        return setLowReservoirTask.set(doseUnit, hours).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS)
    }

    @Inject
    lateinit var updateConnectionTask: UpdateConnectionTask

    fun updateConnection(): Single<PatchState> {
        return updateConnectionTask.update()
    }

    fun stopAeBeep(aeCode: Int): Single<PatchBooleanResponse> {
        return ALARM_ALERT_ERROR_BEEP_STOP.stop(aeCode)
    }

    @Synchronized fun fetchPatchState() {
        updateConnectionTask.enqueue()
    }

    @Inject
    lateinit var patchStateManager: PatchStateManager

    @Throws(Throwable::class) fun onAlarmNotification(notification: AlarmNotification) {
        patchStateManager.updatePatchState(create(notification.patchState, System.currentTimeMillis()))

        if (patchConfig.isActivated) {
            if (!patch.isSeqReady) {
                getSequence().subscribe()
            }
            updateBasal()
            updateInjected(notification, true)
            fetchPatchState()
        }
    }

    @Throws(Throwable::class) private fun onInfoNotification(notification: InfoNotification) {
        readBolusStatusFromNotification(notification)
        updateInjected(notification, false)
        if (notification.isBolusDone) {
            fetchPatchState()
        }
    }

    @Throws(Throwable::class) fun updateInjected(notification: BaseNotification, needSave: Boolean) {
        updatePatchConfig(Consumer { patchConfig: PatchConfig ->
            patchConfig.injectCount = notification.totalInjected
            patchConfig.standardBolusInjectCount = notification.sB_CNT
            patchConfig.extendedBolusInjectCount = notification.eB_CNT
            patchConfig.basalInjectCount = notification.basal_CNT
        }, needSave)
    }

    fun sharedKey(): Single<Boolean> {
        return genKeyPair().flatMap<Boolean>(Function { keyPair: KeyPair ->
            ECPublicToRawBytes(keyPair)
                .flatMap<Boolean>(Function { bytes: ByteArray ->
                    PUBLIC_KEY_SET.send(bytes)
                        .map<ByteArray>(Function { obj: KeyResponse -> obj.getPublicKey() })
                        .map<ECPublicKey>(Function { bytes2: ByteArray -> rawToEncodedECPublicKey(SECP256R1, bytes2) })
                        .map<ByteArray>(Function { publicKey: ECPublicKey -> generateSharedSecret(keyPair.private, publicKey) })
                        .doOnSuccess(Consumer { v: ByteArray -> this.saveShared(v) })
                        .map<Boolean>(Function { true })
                })
        })
            .doOnError(Consumer { aapsLogger.error(LTag.PUMP, "sharedKey error") })
    }

    fun getSequence(): Single<Boolean> {
        return SEQUENCE_GET.get()
            .map<Int>(Function { obj: KeyResponse -> obj.sequence })
            .doOnSuccess(Consumer { sequence: Int ->
                if (sequence >= 0) {
                    saveSequence(sequence)
                }
            })
            .flatMap<Boolean>(Function { Single.just<Boolean>(true) })
    }

    private fun saveShared(v: ByteArray) {
        patchConfig.sharedKey = v
        pm.flushPatchConfig()
    }

    private fun saveSequence(sequence: Int) {
        patch.setSeq(sequence)
        patchConfig.seq15 = sequence
        pm.flushPatchConfig()
    }

    fun genKeyPair(): Single<KeyPair> {
        return Single.fromCallable<KeyPair>(Callable {
            val ecSpec_named = ECGenParameterSpec(SECP256R1)
            val kpg = KeyPairGenerator.getInstance(EC)
            kpg.initialize(ecSpec_named)
            kpg.generateKeyPair()
        })
    }

    fun ECPublicToRawBytes(keyPair: KeyPair): Single<ByteArray> {
        return Single.just<PublicKey>(keyPair.public).cast<ECPublicKey>(ECPublicKey::class.java)
            .map<ByteArray>(Function { pubKey: ECPublicKey -> encodeECPublicKey(pubKey) })
    }

    val patchConnectionState get() = patch.connectionState

    fun observePatchConnectionState(): Observable<BleConnectionState> {
        return patch.observeConnectionState()
    }

    fun updateMacAddress(mac: String, b: Boolean) {
        patch.updateMacAddress(mac, b)
    }

    companion object {

        private const val DEFAULT_API_TIME_OUT: Long = 10 // SECONDS

        //==============================================================================================
        // Security
        //==============================================================================================
        private const val SECP256R1 = "secp256r1"
        private const val EC = "EC"
        private const val ECDH = "ECDH"

        private fun encodeECPublicKey(pubKey: ECPublicKey): ByteArray {
            val keyLengthBytes = (pubKey.params.order.bitLength()
                / Byte.SIZE)
            val publicKeyEncoded = ByteArray(2 * keyLengthBytes)

            var offset = 0

            val x = pubKey.w.affineX
            val xba = x.toByteArray()
            check(
                !(xba.size > keyLengthBytes + 1 || xba.size == keyLengthBytes + 1
                    && xba[0].toInt() != 0)
            ) { "X coordinate of EC public key has wrong size" }

            if (xba.size == keyLengthBytes + 1) {
                System.arraycopy(xba, 1, publicKeyEncoded, offset, keyLengthBytes)
            } else {
                System.arraycopy(
                    xba, 0, publicKeyEncoded, offset + keyLengthBytes
                        - xba.size, xba.size
                )
            }
            offset += keyLengthBytes

            val y = pubKey.w.affineY
            val yba = y.toByteArray()
            check(
                !(yba.size > keyLengthBytes + 1 || yba.size == keyLengthBytes + 1
                    && yba[0].toInt() != 0)
            ) { "Y coordinate of EC public key has wrong size" }

            if (yba.size == keyLengthBytes + 1) {
                System.arraycopy(yba, 1, publicKeyEncoded, offset, keyLengthBytes)
            } else {
                System.arraycopy(
                    yba, 0, publicKeyEncoded, offset + keyLengthBytes
                        - yba.size, yba.size
                )
            }

            return publicKeyEncoded
        }

        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class, InvalidParameterSpecException::class) fun rawToEncodedECPublicKey(curveName: String, rawBytes: ByteArray): ECPublicKey {
            val kf = KeyFactory.getInstance(EC)
            val mid = rawBytes.size / 2
            val x = Arrays.copyOfRange(rawBytes, 0, mid)
            val y = Arrays.copyOfRange(rawBytes, mid, rawBytes.size)
            val w = ECPoint(BigInteger(1, x), BigInteger(1, y))
            return (kf.generatePublic(java.security.spec.ECPublicKeySpec(w, ecParameterSpecForCurve(curveName))) as ECPublicKey)
        }

        @Throws(NoSuchAlgorithmException::class, InvalidParameterSpecException::class) fun ecParameterSpecForCurve(curveName: String): ECParameterSpec {
            val params = AlgorithmParameters.getInstance(EC)
            params.init(ECGenParameterSpec(curveName))
            return params.getParameterSpec<ECParameterSpec>(ECParameterSpec::class.java)
        }

        fun generateSharedSecret(
            privateKey: PrivateKey,
            publicKey: PublicKey
        ): ByteArray {
            val keyAgreement = KeyAgreement.getInstance(ECDH)
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)

            return keyAgreement.generateSecret()
        }

        //==============================================================================================
        // Single Scheduler all callback must be observed on
        //==============================================================================================
        private val SS = Schedulers.single()
    }
}
