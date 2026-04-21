package info.nightscout.androidaps.plugins.pump.carelevo

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoAlarmNotifier
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoBolusCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoSettingsCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParserRegister
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoUpdateBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.CancelBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchTimeZoneUpdateUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchBuzzModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchExpiredThresholdModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.quality.Strictness
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import javax.inject.Provider

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class CarelevoPumpPluginTestBase {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var sp: SP

    @Mock lateinit var fabricPrivacy: FabricPrivacy

    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var iconsProvider: IconsProvider
    @Mock lateinit var context: Context
    @Mock lateinit var bolusProgressData: BolusProgressData

    @Mock lateinit var carelevoProtocolParserRegister: CarelevoProtocolParserRegister
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleController: CarelevoBleController

    @Mock lateinit var setBasalProgramUseCase: CarelevoSetBasalProgramUseCase
    @Mock lateinit var updateBasalProgramUseCase: CarelevoUpdateBasalProgramUseCase
    @Mock lateinit var startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase
    @Mock lateinit var cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase
    @Mock lateinit var startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase
    @Mock lateinit var startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase
    @Mock lateinit var cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase
    @Mock lateinit var cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase
    @Mock lateinit var finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase

    @Mock lateinit var updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase
    @Mock lateinit var updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase
    @Mock lateinit var deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase

    @Mock lateinit var requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase
    @Mock lateinit var carelevoAlarmNotifier: CarelevoAlarmNotifier

    @Mock lateinit var carelevoPatchTimeZoneUpdateUseCase: CarelevoPatchTimeZoneUpdateUseCase
    @Mock lateinit var carelevoPatchExpiredThresholdModifyUseCase: CarelevoPatchExpiredThresholdModifyUseCase
    @Mock lateinit var carelevoPatchBuzzModifyUseCase: CarelevoPatchBuzzModifyUseCase

    protected lateinit var plugin: CarelevoPumpPlugin
    protected lateinit var testProfile: Profile

    protected lateinit var patchInfoSubject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>>
    protected lateinit var infusionInfoSubject: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>>
    protected lateinit var profileSubject: BehaviorSubject<Optional<Profile>>
    protected lateinit var patchStateSubject: BehaviorSubject<Optional<PatchState>>
    @BeforeEach
    fun setupCarelevoPlugin() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.cpu).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.newThread).thenReturn(Schedulers.trampoline())

        whenever(dateUtil.now()).thenReturn(System.currentTimeMillis())
        whenever(rh.gs(any<Int>())).thenReturn("Mocked")

        testProfile = mock()
        whenever(testProfile.getBasal()).thenReturn(1.0)

        patchInfoSubject = BehaviorSubject.createDefault(Optional.of(samplePatchInfo()))
        infusionInfoSubject = BehaviorSubject.createDefault(Optional.of(CarelevoInfusionInfoDomainModel()))
        profileSubject = BehaviorSubject.createDefault(Optional.of(testProfile))
        patchStateSubject = BehaviorSubject.createDefault(Optional.of(PatchState.ConnectedBooted))
        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoSubject)
        whenever(carelevoPatch.infusionInfo).thenReturn(infusionInfoSubject)
        whenever(carelevoPatch.profile).thenReturn(profileSubject)
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)
        doReturn(PatchState.ConnectedBooted).whenever(carelevoPatch).resolvePatchState()
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(carelevoPatch.isCarelevoConnected()).thenReturn(true)
        whenever(carelevoPatch.isBleConnectedNow(any())).thenReturn(true)

        whenever(startImmeBolusInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Success(StartImmeBolusInfusionResponseModel(expectSec = 1)))
        )
        whenever(finishImmeBolusInfusionUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))
        whenever(startExtendBolusInfusionUseCase.execute(any())).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))
        whenever(cancelImmeBolusInfusionUseCase.execute()).thenReturn(
            Single.just(ResponseResult.Success(CancelBolusInfusionResponseModel(infusedAmount = 0.0)))
        )
        whenever(cancelExtendBolusInfusionUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))

        whenever(startTempBasalInfusionUseCase.execute(any())).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))
        whenever(cancelTempBasalInfusionUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))

        whenever(requestPatchInfusionInfoUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))
        whenever(carelevoPatchTimeZoneUpdateUseCase.execute(any())).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))

        val pumpEnactResultProvider = Provider<PumpEnactResult> { FakePumpEnactResult() }
        val basalProfileUpdateCoordinator = CarelevoBasalProfileUpdateCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            notificationManager = notificationManager,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            setBasalProgramUseCase = setBasalProgramUseCase,
            updateBasalProgramUseCase = updateBasalProgramUseCase
        )
        val bolusCoordinator = CarelevoBolusCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            dateUtil = dateUtil,
            bolusProgressData = bolusProgressData,
            pumpSync = pumpSync,
            rxBus = rxBus,
            aapsSchedulers = aapsSchedulers,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            startImmeBolusInfusionUseCase = startImmeBolusInfusionUseCase,
            finishImmeBolusInfusionUseCase = finishImmeBolusInfusionUseCase,
            cancelImmeBolusInfusionUseCase = cancelImmeBolusInfusionUseCase,
            startExtendBolusInfusionUseCase = startExtendBolusInfusionUseCase,
            cancelExtendBolusInfusionUseCase = cancelExtendBolusInfusionUseCase
        )
        val tempBasalCoordinator = CarelevoTempBasalCoordinator(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            dateUtil = dateUtil,
            pumpSync = pumpSync,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            startTempBasalInfusionUseCase = startTempBasalInfusionUseCase,
            cancelTempBasalInfusionUseCase = cancelTempBasalInfusionUseCase
        )
        val connectionCoordinator = CarelevoConnectionCoordinator(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            commandQueue = commandQueue,
            carelevoPatch = carelevoPatch,
            bleController = bleController,
            requestPatchInfusionInfoUseCase = requestPatchInfusionInfoUseCase
        )
        val settingsCoordinator = CarelevoSettingsCoordinator(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            preferences = preferences,
            sp = sp,
            carelevoPatch = carelevoPatch,
            updateMaxBolusDoseUseCase = updateMaxBolusDoseUseCase,
            updateLowInsulinNoticeAmountUseCase = updateLowInsulinNoticeAmountUseCase,
            deleteUserSettingInfoUseCase = deleteUserSettingInfoUseCase,
            carelevoPatchTimeZoneUpdateUseCase = carelevoPatchTimeZoneUpdateUseCase,
            carelevoPatchExpiredThresholdModifyUseCase = carelevoPatchExpiredThresholdModifyUseCase,
            carelevoPatchBuzzModifyUseCase = carelevoPatchBuzzModifyUseCase
        )

        plugin = CarelevoPumpPlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            commandQueue = commandQueue,
            aapsSchedulers = aapsSchedulers,
            rxBus = rxBus,
            sp = sp,
            fabricPrivacy = fabricPrivacy,
            notificationManager = notificationManager,
            profileFunction = profileFunction,
            context = context,
            protectionCheck = protectionCheck,
            blePreCheck = blePreCheck,
            iconsProvider = iconsProvider,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoProtocolParserRegister = carelevoProtocolParserRegister,
            carelevoPatch = carelevoPatch,
            carelevoAlarmNotifier = carelevoAlarmNotifier,
            basalProfileUpdateCoordinator = basalProfileUpdateCoordinator,
            bolusCoordinator = bolusCoordinator,
            tempBasalCoordinator = tempBasalCoordinator,
            connectionCoordinator = connectionCoordinator,
            settingsCoordinator = settingsCoordinator
        )
        plugin.javaClass.getDeclaredField("txUuid").apply {
            isAccessible = true
            set(plugin, java.util.UUID.randomUUID())
        }
    }

    protected fun samplePatchInfo(
        address: String = "AA:BB:CC:DD:EE:FF",
        manufactureNumber: String = "CARELEVO-TEST-001",
        insulinRemain: Double = 60.0,
        bolusActionSeq: Int = 1
    ): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = address,
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            manufactureNumber = manufactureNumber,
            insulinRemain = insulinRemain,
            bolusActionSeq = bolusActionSeq,
            mode = 1
        )

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

        override fun success(success: Boolean): PumpEnactResult = apply { this.success = success }
        override fun enacted(enacted: Boolean): PumpEnactResult = apply { this.enacted = enacted }
        override fun comment(comment: String): PumpEnactResult = apply { this.comment = comment }
        override fun comment(comment: Int): PumpEnactResult = apply { this.comment = comment.toString() }
        override fun duration(duration: Int): PumpEnactResult = apply { this.duration = duration }
        override fun absolute(absolute: Double): PumpEnactResult = apply { this.absolute = absolute }
        override fun percent(percent: Int): PumpEnactResult = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean): PumpEnactResult = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean): PumpEnactResult = apply { this.queued = queued }
    }
}
