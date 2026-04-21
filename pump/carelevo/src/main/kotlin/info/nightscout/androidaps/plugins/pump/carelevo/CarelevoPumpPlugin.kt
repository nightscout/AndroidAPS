package info.nightscout.androidaps.plugins.pump.carelevo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.compose.icons.IcPluginCarelevo
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState.Companion.codeToBondingResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.DeviceModuleState.Companion.codeToDeviceResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralConnectionState
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoAlarmNotifier
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoObserveReceiver
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.compose.CarelevoComposeContent
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoBolusCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoSettingsCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParserRegister
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType.Companion.isCritical
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val notificationManager: NotificationManager,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val iconsProvider: IconsProvider,
    private var pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoProtocolParserRegister: CarelevoProtocolParserRegister,
    private val carelevoPatch: CarelevoPatch,

    private val carelevoAlarmNotifier: CarelevoAlarmNotifier,
    private val basalProfileUpdateCoordinator: CarelevoBasalProfileUpdateCoordinator,
    private val bolusCoordinator: CarelevoBolusCoordinator,
    private val tempBasalCoordinator: CarelevoTempBasalCoordinator,
    private val connectionCoordinator: CarelevoConnectionCoordinator,
    private val settingsCoordinator: CarelevoSettingsCoordinator
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            CarelevoComposeContent(
                aapsLogger = aapsLogger,
                carelevoAlarmNotifier = carelevoAlarmNotifier,
                protectionCheck = protectionCheck,
                blePreCheck = blePreCheck,
                iconsProvider = iconsProvider
            )
        }
        .icon(IcPluginCarelevo)
        .pluginName(R.string.carelevo)
        .shortName(R.string.carelevo_shortname)
        .description(R.string.carelevo_description),
    ownPreferences = listOf(CarelevoBooleanPreferenceKey::class.java, CarelevoIntPreferenceKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump {
    private var bleReceiverDisposable: Disposable? = null
    private val pluginDisposable = CompositeDisposable()

    private var _pumpType: PumpType = PumpType.CAREMEDI_CARELEVO
    private val _pumpDescription = PumpDescription().fillFor(_pumpType)

    private var scope: CoroutineScope? = null

    @Inject @Named("characterTx") lateinit var txUuid: UUID

    override fun onStart() {
        super.onStart()

        applyDefaultCageThresholdsIfNeeded()
        registerPreferenceChangeObserver()
        registerAppInitializedObserver()
        registerBleReceiverIfNeeded()
        startAlarmObserving()
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "onStop called")
        settingsCoordinator.clearUserSettings(pluginDisposable)
        pluginDisposable.clear()
        connectionCoordinator.onStop()
        //carelevoAlarmNotifier.stopObserving()
    }

    private fun registerPreferenceChangeObserver() {
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        preferences.observe(DoubleKey.SafetyMaxBolus)
            .drop(1)
            .onEach {
                settingsCoordinator.updateMaxBolusDose(
                    pluginDisposable = pluginDisposable,
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            }.launchIn(newScope)

        preferences.observe(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)
            .drop(1)
            .onEach {
                settingsCoordinator.updatePatchExpiredThreshold(
                    pluginDisposable = pluginDisposable,
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            }
            .launchIn(newScope)

        preferences.observe(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS)
            .drop(1)
            .onEach {
                settingsCoordinator.updateLowInsulinNoticeAmount(
                    pluginDisposable = pluginDisposable,
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            }
            .launchIn(newScope)

        preferences.observe(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
            .drop(1)
            .onEach {
                settingsCoordinator.updatePatchBuzzer(
                    pluginDisposable = pluginDisposable,
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            }
            .launchIn(newScope)
    }

    private fun registerAppInitializedObserver() {
        pluginDisposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .flatMapCompletable {
                Completable.fromAction {
                    carelevoProtocolParserRegister.registerParser()
                }
                    .doOnSubscribe { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) parser registered start") }
                    .doOnComplete { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) parser registered done") }
                    .andThen(
                        carelevoPatch.initPatchOnce()
                            .timeout(5, TimeUnit.SECONDS)
                            .onErrorComplete()
                            .doOnSubscribe { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 2) initPatchOnce waiting") }
                            .doOnComplete { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 2) initPatchOnce completed") }
                    )
                    .andThen(
                        Single.fromCallable {
                            runBlocking {
                                requireNotNull(profileFunction.getProfile()) { "profile is null" }
                            }
                        }.doOnSuccess { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) getProfile ok: $it") }
                    )
                    .flatMapCompletable { profile ->
                        Completable.fromAction { carelevoPatch.setProfile(profile) }
                            .doOnComplete { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) setProfile done") }
                    }
                    .andThen(
                        Completable.fromAction {
                            aapsLogger.debug(LTag.PUMPCOMM, "onStart: 4) snapshot check start")
                            val state = carelevoPatch.patchState.value?.getOrNull()
                            val shouldReconnect = state == null ||
                                (state != PatchState.NotConnectedNotBooting && state != PatchState.ConnectedBooted)
                            aapsLogger.debug(LTag.PUMPCOMM, "onStart: 4) shouldReconnect=$shouldReconnect, state=$state")
                            if (shouldReconnect) connectionCoordinator.startReconnection(txUuid)
                        }.doOnComplete { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 4) snapshot check done") }
                    )
            }
            .subscribe(
                { aapsLogger.debug(LTag.PUMPCOMM, "onStart: ALL COMPLETE") },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "onStart: chain error", e) }
            )

        pluginDisposable += carelevoPatch.patchInfo
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           _reservoirLevel.value = PumpInsulin(it.getOrNull()?.insulinRemain ?: 0.0)
                           _batteryLevel.value = 0
                       }, fabricPrivacy::logException)
    }

    private fun registerBleReceiverIfNeeded() {
        if (bleReceiverDisposable?.isDisposed == false) return

        bleReceiverDisposable = CarelevoObserveReceiver(context, createBluetoothIntentFilter())
            .subscribe { intent ->
                aapsLogger.debug(LTag.PUMPBTCOMM, "CarelevoObserveReceiver called: ${intent.action}")
                when (intent.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR
                        )
                        CarelevoBleSource.bluetoothState.value
                            ?.copy(isBonded = bondState.codeToBondingResult())
                            ?.let { CarelevoBleSource._bluetoothState.onNext(it) }
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val value = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (value in setOf(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF)) {
                            val isConnected = value == BluetoothAdapter.STATE_ON

                            CarelevoBleSource._bluetoothState.value?.copy(
                                isEnabled = value.codeToDeviceResult(),
                                isConnected = if (isConnected) {
                                    PeripheralConnectionState.CONN_STATE_NONE
                                } else {
                                    CarelevoBleSource._bluetoothState.value?.isConnected ?: PeripheralConnectionState.CONN_STATE_NONE
                                },
                            )?.let { CarelevoBleSource._bluetoothState.onNext(it) }
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> Unit

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> Unit
                }
            }

        bleReceiverDisposable?.let { pluginDisposable.add(it) }
    }

    private fun createBluetoothIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
    }

    private fun applyDefaultCageThresholdsIfNeeded() {
        if (sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key, false)) return

        sp.edit {
            putInt(IntKey.OverviewCageWarning.key, 96)
            putInt(IntKey.OverviewCageCritical.key, 168)
            putBoolean(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key, true)
        }
    }

    fun startAlarmObserving() {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving:: onStart")

        CoroutineScope(Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        aapsLogger.debug(LTag.NOTIFICATION, "Foreground transition -> refresh alarms")
                        carelevoAlarmNotifier.refreshAlarms()
                    }
                }
            )
        }

        carelevoAlarmNotifier.startObserving { alarms ->
            aapsLogger.debug(LTag.NOTIFICATION, "observe alarms size=${alarms.size}, $alarms")
            handleAlarms(alarms)
        }
    }

    private fun handleAlarms(alarms: List<CarelevoAlarmInfo>) {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving handleAlarms:: $alarms")
        if (alarms.isEmpty()) return

        if (
            alarms.any {
                it.alarmType.isCritical() ||
                    it.cause == AlarmCause.ALARM_ALERT_BLUETOOTH_OFF
            }
        ) {
            aapsLogger.debug(LTag.NOTIFICATION, "critical alarm handled by compose host")
        } else {
            carelevoAlarmNotifier.showTopNotification(alarms)
        }

    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "carelevo_settings",
        titleResId = R.string.carelevo,
        items = listOf(
            CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.withEntries(
                (20..50 step 5).associateWith { "$it U" }
            ),
            CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.withEntries(
                (24..167 step 1).associateWith { "$it ${rh.gs(app.aaps.core.interfaces.R.string.hours)}" }
            ),
            CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER
        ),
        icon = pluginDescription.icon
    )

    override fun isInitialized(): Boolean {
        return connectionCoordinator.isInitialized()
    }

    override fun isSuspended(): Boolean {
        val patchState = carelevoPatch.resolvePatchState()
        aapsLogger.debug(LTag.PUMP, "result: $patchState")
        return patchState == PatchState.NotConnectedBooted
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return connectionCoordinator.isConnected()
    }

    override fun isConnecting(): Boolean {
        return false
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun connect(reason: String) {
        connectionCoordinator.connect(
            reason = reason,
            txUuid = txUuid,
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun disconnect(reason: String) {
        connectionCoordinator.disconnect(reason)
    }

    override fun stopConnecting() {
        connectionCoordinator.stopConnecting()
    }

    override fun getPumpStatus(reason: String) {
        connectionCoordinator.refreshPumpStatus(
            pluginDisposable = pluginDisposable,
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile timezoneOrDSTChanged called - ${carelevoPatch.resolvePatchState()}")
         _lastDataTime.value = System.currentTimeMillis()
        val result = when (carelevoPatch.resolvePatchState()) {
            is PatchState.ConnectedBooted -> {
                updateBasalProfile(profile)
            }

            is PatchState.NotConnectedNotBooting -> {
                carelevoPatch.setProfile(profile)
                notificationManager.post(NotificationId.PROFILE_SET_OK, app.aaps.core.ui.R.string.profile_set_ok, validMinutes = 60)
                pumpEnactResultProvider.get().success(true).enacted(true)
            }

            else -> {
                pumpEnactResultProvider.get()
            }
        }
        aapsLogger.debug(LTag.PUMP, "result success=${result.success} enacted=${result.enacted} comment=${result.comment}")
        return result
    }

    private fun updateBasalProfile(profile: Profile): PumpEnactResult {
        return basalProfileUpdateCoordinator.updateBasalProfile(
            profile = profile,
            cancelExtendedBolus = { cancelExtendedBolus() },
            cancelTempBasal = { cancelTempBasal(true) },
            onProfileUpdated = { updatedProfile ->
                 _lastDataTime.value = System.currentTimeMillis()
                carelevoPatch.setProfile(updatedProfile)
            }
        )
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        val checkResult = carelevoPatch.checkIsSameProfile(profile)
        aapsLogger.debug(LTag.PUMP, "checkResult : $checkResult")
        return checkResult
    }

    private val _lastDataTime = MutableStateFlow(0L)
    override val lastDataTime: StateFlow<Long> = _lastDataTime.asStateFlow()

    override val lastBolusTime: StateFlow<Long?>
        get() = bolusCoordinator.lastBolusTime

    override val lastBolusAmount: StateFlow<PumpInsulin?>
        get() = bolusCoordinator.lastBolusAmount

    override val baseBasalRate: PumpRate
        get() = PumpRate(carelevoPatch.profile.value?.getOrNull()?.getBasal() ?: 0.0)

    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel

    // start imme bolus infusion
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return bolusCoordinator.deliverTreatment(
            detailedBolusInfo = detailedBolusInfo,
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() },
            pluginDisposable = pluginDisposable
        )
    }

    // cancel imme bolus
    override fun stopBolusDelivering() {
        bolusCoordinator.cancelImmediateBolus(
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() },
            pluginDisposable = pluginDisposable
        )
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return tempBasalCoordinator.setTempBasalAbsolute(
            absoluteRate = absoluteRate,
            durationInMinutes = durationInMinutes,
            tbrType = tbrType,
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return tempBasalCoordinator.setTempBasalPercent(
            percent = percent,
            durationInMinutes = durationInMinutes,
            tbrType = tbrType,
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        return tempBasalCoordinator.cancelTempBasal(
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        return bolusCoordinator.setExtendedBolus(
            insulin = insulin,
            durationInMinutes = durationInMinutes,
            serialNumber = serialNumber()
        )
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        return bolusCoordinator.cancelExtendedBolus(
            serialNumber = serialNumber(),
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.CareMedi
    }

    override fun model(): PumpType {
        return PumpType.CAREMEDI_CARELEVO
    }

    override fun serialNumber(): String {
        return carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun loadTDDs(): PumpEnactResult {
        return pumpEnactResultProvider.get()
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        super.timezoneOrDSTChanged(timeChangeType)
        settingsCoordinator.timezoneOrDSTChanged(
            pluginDisposable = pluginDisposable,
            onLastDataUpdated = {  _lastDataTime.value = System.currentTimeMillis() }
        )
    }
}
