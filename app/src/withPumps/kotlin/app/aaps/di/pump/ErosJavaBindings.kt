package app.aaps.di.pump

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.driver.manager.OmnipodManager
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.inject.Provider

/**
 * The eros classes that are still **Java**, constructed by hand.
 *
 * Metro generates a factory from an `@Inject` constructor by reading Kotlin IR, which a Java class does
 * not have. A `@Provides` needs none of that - it just calls the constructor - so writing the call out
 * is all it takes to let Metro own a Java class.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ErosJavaBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodRileyLinkCommunicationManager(
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        rileyLinkServiceData: RileyLinkServiceData,
        serviceTaskExecutor: ServiceTaskExecutor,
        rfSpy: RFSpy,
        activePlugin: ActivePlugin,
        rileyLinkUtil: RileyLinkUtil,
        wakeAndTuneTask: Provider<WakeAndTuneTask>,
        radioResponse: Provider<RadioResponse>
    ): OmnipodRileyLinkCommunicationManager =
        OmnipodRileyLinkCommunicationManager(
            aapsLogger, preferences, rileyLinkServiceData, serviceTaskExecutor,
            rfSpy, activePlugin, rileyLinkUtil, wakeAndTuneTask, radioResponse
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAapsOmnipodErosManager(
        delegate: OmnipodManager,
        podStateManager: ErosPodStateManager,
        erosHistory: ErosHistory,
        aapsOmnipodUtil: AapsOmnipodUtil,
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        rh: ResourceHelper,
        omnipodAlertUtil: OmnipodAlertUtil,
        pumpSync: PumpSync,
        uiInteraction: UiInteraction,
        notificationManager: NotificationManager,
        pumpEnactResultProvider: Provider<PumpEnactResult>,
        ch: ConcentrationHelper,
        bolusProgressData: BolusProgressData
    ): AapsOmnipodErosManager =
        AapsOmnipodErosManager(
            delegate, podStateManager, erosHistory, aapsOmnipodUtil, aapsLogger, rxBus, preferences,
            rh, omnipodAlertUtil, pumpSync, uiInteraction, notificationManager, pumpEnactResultProvider,
            ch, bolusProgressData
        )

    /**
     * The pod command delegate. `AapsOmnipodErosManager` used to build this with `new`; it takes it as a
     * parameter now so its command surface can be reached from a test.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodManager(
        aapsLogger: AAPSLogger,
        aapsSchedulers: AapsSchedulers,
        communicationService: OmnipodRileyLinkCommunicationManager,
        podStateManager: ErosPodStateManager
    ): OmnipodManager = OmnipodManager(aapsLogger, aapsSchedulers, communicationService, podStateManager)
}
