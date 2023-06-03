package info.nightscout.androidaps.plugins.pump.eopatch.ble;

import static android.content.Intent.ACTION_DATE_CHANGED;
import static android.content.Intent.ACTION_TIMEZONE_CHANGED;
import static android.content.Intent.ACTION_TIME_CHANGED;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyAgreement;
import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.ActivateTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.DeactivateTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.GetPatchInfoTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.InfoReminderTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.NeedleSensingTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.PauseBasalTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.PrimingTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.ResumeBasalTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.SelfTestTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.SetLowReservoirTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StartBondTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StartCalcBolusTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StartNormalBasalTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StartQuickBolusTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StartTempBasalTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StopComboBolusTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StopExtBolusTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StopNowBolusTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.StopTempBasalTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.TaskBase;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.TaskFunc;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.UpdateConnectionTask;
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;
import info.nightscout.androidaps.plugins.pump.eopatch.code.DeactivationStatus;
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys;
import info.nightscout.androidaps.plugins.pump.eopatch.core.Patch;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BuzzerStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetTemperature;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.PublicKeySend;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SequenceGet;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.StopAeBeep;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.core.noti.AlarmNotification;
import info.nightscout.androidaps.plugins.pump.eopatch.core.noti.BaseNotification;
import info.nightscout.androidaps.plugins.pump.eopatch.core.noti.InfoNotification;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.ComboBolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.KeyResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.BleConnectionState;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.IBleDevice;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchSelfTestResult;
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm;
import info.nightscout.androidaps.plugins.pump.eopatch.ui.receiver.RxBroadcastReceiver;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class PatchManagerImpl {
    @Inject IPreferenceManager pm;
    @Inject Context context;
    @Inject SP sp;
    @Inject AAPSLogger aapsLogger;
    @Inject AapsSchedulers aapsSchedulers;

    @Inject StartBondTask START_BOND;
    @Inject GetPatchInfoTask GET_PATCH_INFO;
    @Inject SelfTestTask SELF_TEST;
    @Inject PrimingTask START_PRIMING;
    @Inject NeedleSensingTask START_NEEDLE_CHECK;

    IBleDevice patch;

    private final CompositeDisposable compositeDisposable;

    private static final long DEFAULT_API_TIME_OUT = 10; // SECONDS

    private final BuzzerStop BUZZER_STOP;
    private final GetTemperature TEMPERATURE_GET;
    private final StopAeBeep ALARM_ALERT_ERROR_BEEP_STOP;
    private final PublicKeySend PUBLIC_KEY_SET;
    private final SequenceGet SEQUENCE_GET;

    @Inject
    public PatchManagerImpl() {
        compositeDisposable = new CompositeDisposable();

        BUZZER_STOP = new BuzzerStop();
        TEMPERATURE_GET = new GetTemperature();
        ALARM_ALERT_ERROR_BEEP_STOP = new StopAeBeep();
        PUBLIC_KEY_SET = new PublicKeySend();
        SEQUENCE_GET = new SequenceGet();
    }

    @Inject
    void onInit() {
        patch = Patch.getInstance();
        patch.init(context);
        patch.setSeq(pm.getPatchConfig().getSeq15());

        IntentFilter filter = new IntentFilter(ACTION_TIME_CHANGED);
        filter.addAction(ACTION_DATE_CHANGED);
        filter.addAction(ACTION_TIMEZONE_CHANGED);

        Observable<Intent> dateTimeChanged = RxBroadcastReceiver.Companion.create(context, filter);

        compositeDisposable.add(
                Observable.combineLatest(patch.observeConnected(), pm.observePatchLifeCycle(),
                                (connected, lifeCycle) -> (connected && lifeCycle.isActivated()))
                        .subscribeOn(aapsSchedulers.getIo())
                        .filter(ok -> ok)
                        .observeOn(aapsSchedulers.getIo())
                        .doOnNext(v -> TaskBase.enqueue(TaskFunc.UPDATE_CONNECTION))
                        .retry()
                        .subscribe());

        compositeDisposable.add(
                Observable.combineLatest(patch.observeConnected(),
                                pm.observePatchLifeCycle().distinctUntilChanged(),
                                dateTimeChanged.startWith(Observable.just(new Intent())),
                                (connected, lifeCycle, value) -> (connected && lifeCycle.isActivated()))
                        .subscribeOn(aapsSchedulers.getIo())
                        .doOnNext(v -> aapsLogger.debug(LTag.PUMP, "Has the date or time changed? " + v))
                        .filter(ok -> ok)
                        .doOnNext(v -> TaskBase.enqueue(TaskFunc.SET_GLOBAL_TIME))
                        .doOnError(e -> aapsLogger.error(LTag.PUMP, "Failed to set EOPatch time."))
                        .retry()
                        .subscribe());

        compositeDisposable.add(
                patch.observeConnected()
                        .doOnNext(this::onPatchConnected)
                        .subscribe());

        compositeDisposable.add(
                pm.getPatchConfig().observe().doOnNext(config -> {
                    byte[] newKey = config.getSharedKey();
                    patch.updateEncryptionParam(newKey);
                }).subscribe()
        );

        compositeDisposable.add(
                EoPatchRxBus.INSTANCE.listen(EventEoPatchAlarm.class)
                        .filter(EventEoPatchAlarm::isFirst)
                        .filter(it -> !pm.getPatchConfig().isDeactivated())
                        .filter(it -> patch.getConnectionState().isConnected())
                        .concatMapIterable(EventEoPatchAlarm::getAlarmCodes)
                        .filter(AlarmCode::isPatchOccurrenceAlert)
                        .subscribe()
        );

        compositeDisposable.add(
                EoPatchRxBus.INSTANCE.listen(EventEoPatchAlarm.class)
                        .filter(EventEoPatchAlarm::isFirst)
                        .filter(it -> !pm.getPatchConfig().isDeactivated())
                        .filter(it -> patch.getConnectionState().isConnected())
                        .concatMapIterable(EventEoPatchAlarm::getAlarmCodes)
                        .filter(AlarmCode::isPatchOccurrenceAlarm)
                        .flatMap(it -> pauseBasalImpl(0.0f, System.currentTimeMillis(), it).toObservable())
                        .subscribe()
        );


        monitorPatchNotification();
        onConnectedUpdateSequence();
    }

    private void onPatchConnected(boolean connected) {
        boolean activated = pm.getPatchConfig().isActivated();
        boolean useEncryption = pm.getPatchConfig().getSharedKey() != null;
        int doseUnit = sp.getInt(SettingKeys.Companion.getLOW_RESERVOIR_REMINDERS(), 0);
        int hours = sp.getInt(SettingKeys.Companion.getEXPIRATION_REMINDERS(), 0);
        boolean buzzer = sp.getBoolean(SettingKeys.Companion.getBUZZER_REMINDERS(), false);
        PatchConfig pc = pm.getPatchConfig();

        if (connected && activated && useEncryption) {
            compositeDisposable.add(
                    SEQUENCE_GET.get()
                            .map(KeyResponse::getSequence)
                            .doOnSuccess(sequence -> {
                                if (sequence >= 0) {
                                    saveSequence(sequence);
                                }
                            })
                            .flatMap(integer -> {
                                if (pc.getLowReservoirAlertAmount() != doseUnit || pc.getPatchExpireAlertTime() != hours) {
                                    return setLowReservoir(doseUnit, hours)
                                            .doOnSuccess(patchBooleanResponse -> {
                                                pc.setLowReservoirAlertAmount(doseUnit);
                                                pc.setPatchExpireAlertTime(hours);
                                                pm.flushPatchConfig();
                                            }).map(patchBooleanResponse -> true);
                                }
                                return Single.just(true);
                            })
                            .flatMap(ret -> {
                                if (pc.getInfoReminder() != buzzer) {
                                    return infoReminderSet(buzzer)
                                            .doOnSuccess(patchBooleanResponse -> {
                                                pc.setInfoReminder(buzzer);
                                                pm.flushPatchConfig();
                                            }).map(patchBooleanResponse -> true);
                                }
                                return Single.just(true);
                            })
                            .flatMap(ret -> {
                                if(!pm.getAlarms().getNeedToStopBeep().isEmpty()) {
                                    return Observable.fromStream(pm.getAlarms().getNeedToStopBeep().stream())
                                            .flatMapSingle(alarmCode -> stopAeBeep(alarmCode.getAeCode()).doOnSuccess(patchBooleanResponse -> {
                                                pm.getAlarms().getNeedToStopBeep().remove(alarmCode);
                                            }))
                                            .lastOrError();
                                }
                                return Single.just(true);
                            })
                            .subscribe());
        }

        if (!connected && activated) {
            pm.getPatchConfig().updatetDisconnectedTime();
        }
    }

    private void monitorPatchNotification() {
        compositeDisposable.addAll(
                patch.observeAlarmNotification()
                        .subscribe(
                                this::onAlarmNotification,
                                throwable -> aapsLogger.error(LTag.PUMP, throwable.getMessage() != null ?
                                        throwable.getMessage() : "AlarmNotification observation error")
                        ),
                patch.observeInfoNotification()
                        .filter(state -> pm.getPatchConfig().isActivated())
                        .subscribe(
                                this::onInfoNotification,
                                throwable -> aapsLogger.error(LTag.PUMP, throwable.getMessage() != null ?
                                        throwable.getMessage() : "InfoNotification observation error")
                        )
        );
    }


    private void onConnectedUpdateSequence() {

    }

    //==============================================================================================
    // preference database update helper
    //==============================================================================================

    // synchronized lock
    private final Object lock = new Object();

    private void updatePatchConfig(Consumer<PatchConfig> consumer, boolean needSave) throws Throwable {
        synchronized (lock) {
            consumer.accept(pm.getPatchConfig());
            if (needSave) {
                pm.flushPatchConfig();
            }
        }
    }

    synchronized void updateBasal() {

        NormalBasal normalBasal = pm.getNormalBasalManager().getNormalBasal();

        if (normalBasal.updateNormalBasalIndex()) {
            pm.flushNormalBasalManager();
        }
    }

    public void connect() {

    }

    public void disconnect() {
    }

    /**
     * getPatchConnection() 을 사용해야 한다.
     * 아직 Life Cycle 이 Activated 가 아님.
     * <p>
     * Activation Process task #1 Get Patch Information from Patch
     * Fragment: fragment_patch_connect_new
     */

    public Single<Boolean> startBond(String mac) {
        return START_BOND.start(mac);
    }

    public Single<Boolean> getPatchInfo(long timeout) {
        return GET_PATCH_INFO.get().timeout(timeout, TimeUnit.MILLISECONDS);
    }


    /**
     * Activation Process task #2 Check Patch is O.K
     * Fragment: fragment_patch_connect_new
     */
    public Single<PatchSelfTestResult> selfTest(long timeout) {
        return SELF_TEST.start().timeout(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Activation Process task #3 PRIMING
     * Fragment: fragment_patch_priming
     */

    public Single<TemperatureResponse> getTemperature() {
        return TEMPERATURE_GET.get()
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Observable<Long> startPriming(long timeout, long count) {
        return START_PRIMING.start(count)
                .timeout(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Activation Process task #4 NEEDLE SENSING
     * Fragment: fragment_patch_rotate_knob
     */
    public Single<Boolean> checkNeedleSensing(long timeout) {
        return START_NEEDLE_CHECK.start()
                .timeout(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Activation Process task #5 Activation Secure Key, Basal writing
     * Fragment: fragment_patch_check_patch
     */
    @Inject
    ActivateTask ACTIVATE;

    public Single<Boolean> patchActivation(long timeout) {

        return ACTIVATE.start().timeout(timeout, TimeUnit.MILLISECONDS)
                .flatMap(success -> sharedKey())
                .flatMap(success -> getSequence())
                .doOnSuccess(success -> {
                    if (success) {
                        TaskBase.enqueue(TaskFunc.LOW_RESERVOIR);
                        TaskBase.enqueue(TaskFunc.INFO_REMINDER);
                    }
                });
    }


    //==============================================================================================
    // IPatchManager interface [NORMAL BASAL]
    //==============================================================================================

    @Inject
    StartNormalBasalTask startNormalBasalTask;

    public Single<BasalScheduleSetResponse> startBasal(NormalBasal basal) {

        return startNormalBasalTask.start(basal)
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    @Inject
    ResumeBasalTask resumeBasalTask;

    public Single<? extends BaseResponse> resumeBasal() {
        return resumeBasalTask.resume()
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Single<? extends BaseResponse> pauseBasal(float pauseDurationHour) {
        return pauseBasalImpl(pauseDurationHour, 0, null)
                .observeOn(SS)
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    //==============================================================================================
    // IPatchManager implementation [NORMAL BASAL]
    //==============================================================================================

    @Inject
    PauseBasalTask pauseBasalTask;

    private Single<? extends BaseResponse> pauseBasalImpl(float pauseDurationHour, long alarmOccurredTime, @Nullable AlarmCode alarmCode) {
        return pauseBasalTask.pause(pauseDurationHour, alarmOccurredTime, alarmCode);
    }

    //==============================================================================================
    // IPatchManager interface [TEMP BASAL]
    //==============================================================================================

    @Inject
    StartTempBasalTask startTempBasalTask;

    public Single<TempBasalScheduleSetResponse> startTempBasal(TempBasal tempBasal) {
        return startTempBasalTask.start(tempBasal).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    // 템프베이젤 주입 정지
    // 템프베이젤이 정지되면 자동으로 노멀베이젤이 활성화된다
    // 외부에서 호출된다. 즉 명시적으로 tempBasal 정지. 이 때는 normalBasal resume 은 PatchState 보고 처리.

    @Inject
    StopTempBasalTask stopTempBasalTask;

    public Single<PatchBooleanResponse> stopTempBasal() {
        return stopTempBasalTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    @Inject
    StartQuickBolusTask startQuickBolusTask;

    @Inject
    StartCalcBolusTask startCalcBolusTask;

    @Inject
    StopComboBolusTask stopComboBolusTask;

    @Inject
    StopNowBolusTask stopNowBolusTask;

    @Inject
    StopExtBolusTask stopExtBolusTask;


    public Single<? extends BolusResponse> startQuickBolus(float nowDoseU, float exDoseU,
                                                           BolusExDuration exDuration) {
        return startQuickBolusTask.start(nowDoseU, exDoseU, exDuration)
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Single<? extends BolusResponse> startCalculatorBolus(DetailedBolusInfo detailedBolusInfo) {
        return startCalcBolusTask.start(detailedBolusInfo)
                .timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Single<BolusStopResponse> stopNowBolus() {
        return stopNowBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Single<BolusStopResponse> stopExtBolus() {
        return stopExtBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    public Single<ComboBolusStopResponse> stopComboBolus() {
        return stopComboBolusTask.stop().timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

//    private Single<? extends BaseResponse> stopNowAndExtBolus() {
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

    public void readBolusStatusFromNotification(InfoNotification infoNotification) {
        if (infoNotification.isBolusRegAct()) {
            BolusCurrent bolusCurrent = pm.getBolusCurrent();

            Arrays.asList(BolusType.NOW, BolusType.EXT).forEach(type -> {
                if (infoNotification.isBolusRegAct(type)) { // 완료되었어도 업데이트 필요.
                    int injectedPumpCount = infoNotification.getInjected(type);
                    int remainPumpCount = infoNotification.getRemain(type);
                    bolusCurrent.updateBolusFromPatch(type, injectedPumpCount, remainPumpCount);
                }
            });
            pm.flushBolusCurrent();
        }
    }


    @Inject
    DeactivateTask deactivateTask;

    // Patch Activation Tasks
    public Single<DeactivationStatus> deactivate(long timeout, boolean force) {
        return deactivateTask.run(force, timeout);
    }

    @Inject
    InfoReminderTask infoReminderTask;

    public Single<PatchBooleanResponse> infoReminderSet(boolean infoReminder) {
        return infoReminderTask.set(infoReminder).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    @Inject
    SetLowReservoirTask setLowReservoirTask;

    public Single<PatchBooleanResponse> setLowReservoir(int doseUnit, int hours) {
        return setLowReservoirTask.set(doseUnit, hours).timeout(DEFAULT_API_TIME_OUT, TimeUnit.SECONDS);
    }

    @Inject
    UpdateConnectionTask updateConnectionTask;

    public Single<PatchState> updateConnection() {
        return updateConnectionTask.update();
    }

    public Single<PatchBooleanResponse> stopAeBeep(int aeCode) {
        return ALARM_ALERT_ERROR_BEEP_STOP.stop(aeCode);
    }

    synchronized void fetchPatchState() {
        updateConnectionTask.enqueue();
    }

    @Inject
    PatchStateManager patchStateManager;

    void onAlarmNotification(AlarmNotification notification) throws Throwable {
        patchStateManager.updatePatchState(PatchState.create(notification.patchState, System.currentTimeMillis()));

        if (pm.getPatchConfig().isActivated()) {
            if (!patch.isSeqReady()) {
                getSequence().subscribe();
            }
            updateBasal();
            updateInjected(notification, true);
            fetchPatchState();
        }
    }

    private void onInfoNotification(InfoNotification notification) throws Throwable {
        readBolusStatusFromNotification(notification);
        updateInjected(notification, false);
        if (notification.isBolusDone()) {
            fetchPatchState();
        }
    }

    void updateInjected(BaseNotification notification, boolean needSave) throws Throwable {
        updatePatchConfig(patchConfig -> {
            patchConfig.setInjectCount(notification.getTotalInjected());
            patchConfig.setStandardBolusInjectCount(notification.getSB_CNT());
            patchConfig.setExtendedBolusInjectCount(notification.getEB_CNT());
            patchConfig.setBasalInjectCount(notification.getBasal_CNT());
        }, needSave);
    }

    //==============================================================================================
    // Security
    //==============================================================================================
    private static final String SECP256R1 = "secp256r1";
    private static final String EC = "EC";
    private static final String ECDH = "ECDH";

    public Single<Boolean> sharedKey() {
        return genKeyPair().flatMap(keyPair -> ECPublicToRawBytes(keyPair)
                        .flatMap(bytes -> PUBLIC_KEY_SET.send(bytes)
                                .map(KeyResponse::getPublicKey)
                                .map(bytes2 -> rawToEncodedECPublicKey(SECP256R1, bytes2))
                                .map(publicKey -> generateSharedSecret(keyPair.getPrivate(), publicKey))
                                .doOnSuccess(this::saveShared).map(v2 -> true)))
                .doOnError(e -> aapsLogger.error(LTag.PUMP, "sharedKey error"));
    }

    public Single<Boolean> getSequence() {
        return SEQUENCE_GET.get()
                .map(KeyResponse::getSequence)
                .doOnSuccess(sequence -> {
                    if (sequence >= 0) {
                        saveSequence(sequence);
                    }
                })
                .flatMap(v -> Single.just(true));
    }

    private void saveShared(byte[] v) {
        pm.getPatchConfig().setSharedKey(v);
        pm.flushPatchConfig();
    }

    private void saveSequence(int sequence) {
        patch.setSeq(sequence);
        pm.getPatchConfig().setSeq15(sequence);
        pm.flushPatchConfig();
    }

    public Single<KeyPair> genKeyPair() {
        return Single.fromCallable(() -> {
            ECGenParameterSpec ecSpec_named = new ECGenParameterSpec(SECP256R1);
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(EC);
            kpg.initialize(ecSpec_named);
            return kpg.generateKeyPair();
        });
    }

    public Single<byte[]> ECPublicToRawBytes(KeyPair keyPair) {
        return Single.just(keyPair.getPublic()).cast(ECPublicKey.class)
                .map(PatchManagerImpl::encodeECPublicKey);
    }

    private static byte[] encodeECPublicKey(ECPublicKey pubKey) {
        int keyLengthBytes = pubKey.getParams().getOrder().bitLength()
                / Byte.SIZE;
        byte[] publicKeyEncoded = new byte[2 * keyLengthBytes];

        int offset = 0;

        BigInteger x = pubKey.getW().getAffineX();
        byte[] xba = x.toByteArray();
        if (xba.length > keyLengthBytes + 1 || xba.length == keyLengthBytes + 1
                && xba[0] != 0) {
            throw new IllegalStateException(
                    "X coordinate of EC public key has wrong size");
        }

        if (xba.length == keyLengthBytes + 1) {
            System.arraycopy(xba, 1, publicKeyEncoded, offset, keyLengthBytes);
        } else {
            System.arraycopy(xba, 0, publicKeyEncoded, offset + keyLengthBytes
                    - xba.length, xba.length);
        }
        offset += keyLengthBytes;

        BigInteger y = pubKey.getW().getAffineY();
        byte[] yba = y.toByteArray();
        if (yba.length > keyLengthBytes + 1 || yba.length == keyLengthBytes + 1
                && yba[0] != 0) {
            throw new IllegalStateException(
                    "Y coordinate of EC public key has wrong size");
        }

        if (yba.length == keyLengthBytes + 1) {
            System.arraycopy(yba, 1, publicKeyEncoded, offset, keyLengthBytes);
        } else {
            System.arraycopy(yba, 0, publicKeyEncoded, offset + keyLengthBytes
                    - yba.length, yba.length);
        }

        return publicKeyEncoded;
    }

    public static ECPublicKey rawToEncodedECPublicKey(String curveName, byte[] rawBytes) throws
            NoSuchAlgorithmException, InvalidKeySpecException, InvalidParameterSpecException {
        KeyFactory kf = KeyFactory.getInstance(EC);
        int mid = rawBytes.length / 2;
        byte[] x = Arrays.copyOfRange(rawBytes, 0, mid);
        byte[] y = Arrays.copyOfRange(rawBytes, mid, rawBytes.length);
        ECPoint w = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(w, ecParameterSpecForCurve(curveName)));
    }

    public static ECParameterSpec ecParameterSpecForCurve(String curveName) throws
            NoSuchAlgorithmException, InvalidParameterSpecException {
        AlgorithmParameters params = AlgorithmParameters.getInstance(EC);
        params.init(new ECGenParameterSpec(curveName));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    public static byte[] generateSharedSecret(PrivateKey privateKey,
                                              PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(ECDH);
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);

            return keyAgreement.generateSecret();
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    //==============================================================================================
    // Single Scheduler all callback must be observed on
    //==============================================================================================

    private static final Scheduler SS = Schedulers.single();

    public BleConnectionState getPatchConnectionState() {
        return patch.getConnectionState();
    }

    public Observable<BleConnectionState> observePatchConnectionState() {
        return patch.observeConnectionState();
    }

    public void updateMacAddress(String mac, boolean b) {
        patch.updateMacAddress(mac, b);
    }
}
