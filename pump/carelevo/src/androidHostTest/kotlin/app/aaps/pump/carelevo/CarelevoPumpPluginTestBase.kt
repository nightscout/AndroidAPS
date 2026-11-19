package app.aaps.pump.carelevo

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.BolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.BolusCancelResponse
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelResponse
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusResponse
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusCommand
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusResponse
import app.aaps.pump.carelevo.ble.commands.InfusionInfoResponse
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoBolusCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class CarelevoPumpPluginTestBase {

    private val decimalFormatter: DecimalFormatter = mock<DecimalFormatter>().also {
        whenever(it.to2Decimal(any<Double>())).thenAnswer { inv -> "%.2f".format(Locale.US, inv.getArgument<Double>(0)) }
    }

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync

    @Mock lateinit var fabricPrivacy: FabricPrivacy

    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var iconsProvider: IconsProvider
    @Mock lateinit var config: Config
    protected val adapterState = MutableStateFlow(AdapterState.ON)
    protected val appForeground = FakeCarelevoAppForeground()
    @Mock lateinit var bolusProgressData: BolusProgressData

    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var activationExecutor: CarelevoActivationExecutor

    @Mock lateinit var setBasalProgramUseCase: CarelevoSetBasalProgramUseCase
    @Mock lateinit var startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase
    @Mock lateinit var cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase
    @Mock lateinit var startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase
    @Mock lateinit var startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase
    @Mock lateinit var cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase
    @Mock lateinit var cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase
    @Mock lateinit var finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase

    @Mock lateinit var carelevoAlarmNotifier: CarelevoAlarmNotifier

    protected lateinit var plugin: CarelevoPumpPlugin
    protected lateinit var testProfile: Profile

    protected lateinit var patchInfoFlow: MutableSharedFlow<CarelevoPatchInfoDomainModel?>
    protected lateinit var infusionInfoFlow: MutableSharedFlow<CarelevoInfusionInfoDomainModel?>
    protected lateinit var profileFlow: MutableSharedFlow<Profile?>
    protected lateinit var patchStateFlow: MutableSharedFlow<PatchState?>
    protected lateinit var btStateFlow: MutableSharedFlow<BleState?>

    @BeforeEach
    fun setupCarelevoPlugin() {
        whenever(dateUtil.now()).thenReturn(System.currentTimeMillis())
        whenever(rh.gs(any<TextRef>())).thenReturn("Mocked")

        testProfile = mock()
        whenever(testProfile.getBasal()).thenReturn(1.0)

        patchInfoFlow = latestValueFlow<CarelevoPatchInfoDomainModel?>().apply { tryEmit(samplePatchInfo()) }
        infusionInfoFlow = latestValueFlow<CarelevoInfusionInfoDomainModel?>().apply { tryEmit(CarelevoInfusionInfoDomainModel()) }
        profileFlow = latestValueFlow<Profile?>().apply { tryEmit(testProfile) }
        patchStateFlow = latestValueFlow<PatchState?>().apply { tryEmit(PatchState.ConnectedBooted) }
        btStateFlow = latestValueFlow<BleState?>().apply { tryEmit(connectedBleState()) }
        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoFlow)
        whenever(carelevoPatch.infusionInfo).thenReturn(infusionInfoFlow)
        whenever(carelevoPatch.profile).thenReturn(profileFlow)
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow)
        whenever(carelevoPatch.btState).thenReturn(btStateFlow)
        doReturn(PatchState.ConnectedBooted).whenever(carelevoPatch).resolvePatchState()
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)

        whenever { finishImmeBolusInfusionUseCase.execute() }.thenReturn(ResponseResult.Success(ResultSuccess))

        // The coordinators/plugin run everything over the gateway/session; stub the happy path so the
        // plugin tests exercise the success flows.
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn("AA:BB:CC:DD:EE:FF")
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }.thenReturn(SimpleResultResponse(0))
        whenever { bleSession.runSingle(any(), isA<TempBasalCancelCommand>(), any()) }.thenReturn(SimpleResultResponse(0))
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }
            .thenReturn(ImmediateBolusResponse(actionId = 1, resultCode = 0, expectedCompletionSeconds = 1, remainingReservoirUnits = 60.0))
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenReturn(BolusCancelResponse(resultCode = 0, infusedAmount = 0.0))
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCommand>(), any()) }
            .thenReturn(ExtendBolusResponse(resultCode = 0, expectedTimeSeconds = 60))
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCancelCommand>(), any()) }
            .thenReturn(ExtendBolusCancelResponse(resultCode = 0, infusedAmount = 0.0))
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(true)
        whenever(startTempBasalInfusionUseCase.persistTempBasalStarted(any())).thenReturn(true)
        whenever(cancelTempBasalInfusionUseCase.persistTempBasalCancelled()).thenReturn(true)
        whenever(startImmeBolusInfusionUseCase.persistImmeBolusStarted(any(), any(), any(), any(), any(), anyOrNull())).thenReturn(true)
        // An unstubbed suspend mock answers null; false = the record of this pump id existed and was updated.
        whenever { pumpSync.syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) }.thenReturn(false)
        whenever(cancelImmeBolusInfusionUseCase.persistImmeBolusCancelled()).thenReturn(true)
        whenever(startExtendBolusInfusionUseCase.persistExtendBolusStarted(any(), any(), any(), any())).thenReturn(true)
        whenever(cancelExtendBolusInfusionUseCase.persistExtendBolusCancelled()).thenReturn(true)
        // As for an extended bolus started by an older build; a mock would answer 0.
        whenever(cancelExtendBolusInfusionUseCase.runningExtendBolusPumpId()).thenReturn(null)
        // Unstubbed suspend mocks answer null, which a caller that reads the Boolean cannot take.
        whenever { pumpSync.syncExtendedBolusWithPumpId(any(), any(), any(), any(), any(), any(), any()) }.thenReturn(false)
        whenever { pumpSync.syncStopExtendedBolusWithPumpId(any(), any(), any(), any()) }.thenReturn(true)
        whenever(setBasalProgramUseCase.buildBasalProgramPlan(any())).thenReturn(
            CarelevoSetBasalProgramUseCase.BasalProgramPlan(programs = List(3) { List(8) { 1.0 } }, segments = emptyList())
        )
        whenever(setBasalProgramUseCase.persistBasalProgram(any())).thenReturn(true)

        val pumpEnactResultProvider = Provider<PumpEnactResult> { FakePumpEnactResult() }
        val basalProfileUpdateCoordinator = CarelevoBasalProfileUpdateCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            setBasalProgramUseCase = setBasalProgramUseCase
        )
        val bolusCoordinator = CarelevoBolusCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            dateUtil = dateUtil,
            bolusProgressData = bolusProgressData,
            pumpSync = pumpSync,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            startImmeBolusInfusionUseCase = startImmeBolusInfusionUseCase,
            finishImmeBolusInfusionUseCase = finishImmeBolusInfusionUseCase,
            cancelImmeBolusInfusionUseCase = cancelImmeBolusInfusionUseCase,
            startExtendBolusInfusionUseCase = startExtendBolusInfusionUseCase,
            cancelExtendBolusInfusionUseCase = cancelExtendBolusInfusionUseCase,
            decimalFormatter = decimalFormatter
        )
        // The record of a bolus end is written on the test thread, before deliverTreatment returns.
        bolusCoordinator.dispatcher = Dispatchers.Unconfined
        val tempBasalCoordinator = CarelevoTempBasalCoordinator(
            aapsLogger = aapsLogger,
            dateUtil = dateUtil,
            pumpSync = pumpSync,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            startTempBasalInfusionUseCase = startTempBasalInfusionUseCase,
            cancelTempBasalInfusionUseCase = cancelTempBasalInfusionUseCase
        )
        val connectionCoordinator = CarelevoConnectionCoordinator(
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession
        )
        plugin = CarelevoPumpPlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            commandQueue = commandQueue,
            fabricPrivacy = fabricPrivacy,
            profileFunction = profileFunction,
            central = bleCentralWith(adapterState),
            appForeground = appForeground,
            protectionCheck = protectionCheck,
            bleReadyCheck = FakeCarelevoBleReadyCheck(),
            iconsProvider = iconsProvider,
            config = config,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            carelevoAlarmNotifier = carelevoAlarmNotifier,
            basalProfileUpdateCoordinator = basalProfileUpdateCoordinator,
            bolusCoordinator = bolusCoordinator,
            tempBasalCoordinator = tempBasalCoordinator,
            connectionCoordinator = connectionCoordinator,
            activationExecutor = activationExecutor,
            notificationManager = mock()
        )
        // Set before any onStart: everything the plugin collects runs on the test thread.
        plugin.dispatcher = Dispatchers.Unconfined
        plugin.bleSession = bleSession
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(
            InfusionInfoResponse(
                subId = 0,
                runningMinutes = 100,
                insulinRemaining = 60.0,
                infusedTotalBasalAmount = 1.0,
                infusedTotalBolusAmount = 2.0,
                pumpStateRaw = 0,
                modeRaw = 1,
                currentInfusedProgramVolume = 0.0,
                realInfusedTime = 0
            )
        )
    }

    /** Fully-ready link (bonded + discovered + notifications) so `BleState.isConnected()` returns true. */
    protected fun connectedBleState(): BleState =
        BleState(
            isEnabled = DeviceModuleState.DEVICE_STATE_ON,
            isBonded = BondingState.BOND_BONDED,
            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
            isConnected = PeripheralConnectionState.CONN_STATE_CONNECTED,
            isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED
        )

    protected fun samplePatchInfo(
        address: String = "AA:BB:CC:DD:EE:FF",
        manufactureNumber: String = "CARELEVO-TEST-001",
        insulinRemain: Double = 60.0,
        bolusActionSeq: Int = 1
    ): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = address,
            createdAt = Clock.System.now() - 1.hours,
            updatedAt = Clock.System.now(),
            manufactureNumber = manufactureNumber,
            insulinRemain = insulinRemain,
            bolusActionSeq = bolusActionSeq,
            mode = 1,
            activationComplete = true
        )

    /** A concrete [PumpEnactResult] for stubbing suspend queue calls (e.g. `commandQueue.customCommand`). */
    protected fun fakePumpEnactResult(success: Boolean = true): PumpEnactResult =
        FakePumpEnactResult().success(success).enacted(success)

    private class FakePumpEnactResult : PumpEnactResult {

        override var success: Boolean = false
        override var enacted: Boolean = false
        override var comment: String = ""
        override var duration: Int = -1
        override var absolute: Double = -1.0
        override var percent: Int = -1
        override var isPercent: Boolean = false
        override var isTempCancel: Boolean = false
        override var bolusDelivered: Double = 0.0
        override var queued: Boolean = false
        override var cancelled: Boolean = false

        override fun success(success: Boolean): PumpEnactResult = apply { this.success = success }
        override fun enacted(enacted: Boolean): PumpEnactResult = apply { this.enacted = enacted }
        override fun comment(comment: String): PumpEnactResult = apply { this.comment = comment }
        override fun comment(ref: TextRef): PumpEnactResult = apply { this.comment = ref.toString() }
        override fun duration(duration: Int): PumpEnactResult = apply { this.duration = duration }
        override fun absolute(absolute: Double): PumpEnactResult = apply { this.absolute = absolute }
        override fun percent(percent: Int): PumpEnactResult = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean): PumpEnactResult = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean): PumpEnactResult = apply { this.queued = queued }
        override fun cancelled(cancelled: Boolean): PumpEnactResult = apply { this.cancelled = cancelled }
    }
}
