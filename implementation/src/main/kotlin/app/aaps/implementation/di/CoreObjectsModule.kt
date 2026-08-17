package app.aaps.implementation.di

import android.content.Context
import android.telephony.SmsManager
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Constructs the :core:objects classes.
 *
 * They carry no `@Inject constructor` of their own, because `javax.inject` is JVM only and would
 * keep that module from ever becoming multiplatform. Providing them here keeps the graph identical -
 * same scopes, same instances - and is the same trade already made for `BolusProgressData`.
 *
 * Lives in :implementation rather than :core:objects for a second reason: Dagger emits Java, and the
 * AGP multiplatform library target has no Java compilation, so a `@Module` inside a multiplatform
 * module would be generated and then silently dropped.
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
class CoreObjectsModule {

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? = context.getSystemService(SmsManager::class.java)

    @Provides
    @Singleton
    fun provideCryptoUtil(aapsLogger: AAPSLogger): CryptoUtil = CryptoUtil(aapsLogger)

    @Provides
    @Singleton
    fun provideRunningModeGuard(loop: Loop, rh: ResourceHelper, rxBus: RxBus): RunningModeGuard =
        RunningModeGuard(loop, rh, rxBus)

    @Provides
    @Singleton
    fun provideQuickWizard(preferences: Preferences, quickWizardEntry: Provider<QuickWizardEntry>): QuickWizard =
        QuickWizard(preferences) { quickWizardEntry.get() }

    @Provides
    fun provideQuickWizardEntry(
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        profileFunction: ProfileFunction,
        loop: Loop,
        iobCobCalculator: IobCobCalculator,
        persistenceLayer: PersistenceLayer,
        dateUtil: DateUtil,
        glucoseStatusProvider: GlucoseStatusProvider,
        bolusWizard: Provider<BolusWizard>,
        quickWizard: Provider<QuickWizard>
    ): QuickWizardEntry = QuickWizardEntry(
        aapsLogger, preferences, profileFunction, loop, iobCobCalculator, persistenceLayer, dateUtil,
        glucoseStatusProvider, { bolusWizard.get() }, { quickWizard.get() }
    )

    @Suppress("LongParameterList")
    @Provides
    fun provideBolusWizard(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        rxBus: RxBus,
        preferences: Preferences,
        profileFunction: ProfileFunction,
        profileUtil: ProfileUtil,
        constraintChecker: ConstraintsChecker,
        loop: Loop,
        iobCobCalculator: IobCobCalculator,
        dateUtil: DateUtil,
        config: Config,
        uel: UserEntryLogger,
        automation: Automation,
        glucoseStatusProvider: GlucoseStatusProvider,
        persistenceLayer: PersistenceLayer,
        processedDeviceStatusData: ProcessedDeviceStatusData,
        runningModeGuard: RunningModeGuard,
        ch: ConcentrationHelper,
        wizardBolusExecutor: WizardBolusExecutor,
        @ApplicationScope appScope: CoroutineScope
    ): BolusWizard = BolusWizard(
        aapsLogger, rh, rxBus, preferences, profileFunction, profileUtil, constraintChecker, loop,
        iobCobCalculator, dateUtil, config, uel, automation, glucoseStatusProvider, persistenceLayer,
        processedDeviceStatusData, runningModeGuard, ch, wizardBolusExecutor, appScope
    )
}
