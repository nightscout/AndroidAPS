package app.aaps.core.objects.di

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/**
 * Metro wiring for `:core:objects`, the module with the dependency cycle.
 *
 * `QuickWizard` needs a `QuickWizardEntry`, and an entry needs the `QuickWizard` back plus a
 * `BolusWizard`. This is the case that most stresses a compile-time-checked graph, because a strict
 * checker has to be told where the cycle is legitimately broken rather than rejecting it outright.
 *
 * Both AAPS classes already take `() -> T` lambdas rather than `javax.inject.Provider`, since
 * javax.inject is JVM-only and these are multiplatform classes. Metro's own [Provider] is the
 * deferred handle the graph understands, and it converts to the lambda the constructors want.
 *
 * `CryptoUtil` is deliberately absent - its class is in this module's **androidMain**, so it cannot be
 * part of a commonMain graph and stays on Dagger. Source set decides.
 */
@BindingContainer
object CoreObjectsGraph {

    @SingleIn(AppScope::class)
    @Provides
    fun provideRunningModeGuard(loop: Loop, text: TextResolver, bus: RxBus): RunningModeGuard =
        RunningModeGuard(loop, text, bus)

    // The cycle. A Metro Provider is a deferred lookup, so the graph accepts it where a direct
    // reference would be a cycle error.
    @SingleIn(AppScope::class)
    @Provides
    fun provideQuickWizard(prefs: Preferences, entry: Provider<QuickWizardEntry>): QuickWizard =
        QuickWizard(prefs) { entry() }

    @Provides
    fun provideQuickWizardEntry(
        logger: AAPSLogger,
        prefs: Preferences,
        profile: ProfileFunction,
        loopRef: Loop,
        iobCob: IobCobCalculator,
        persistence: PersistenceLayer,
        dates: DateUtil,
        glucose: GlucoseStatusProvider,
        wizard: Provider<BolusWizard>,
        quick: Provider<QuickWizard>
    ): QuickWizardEntry = QuickWizardEntry(
        logger, prefs, profile, loopRef, iobCob, persistence, dates, glucose, { wizard() }, { quick() }
    )

    @Suppress("LongParameterList")
    @Provides
    fun provideBolusWizard(
        logger: AAPSLogger,
        text: TextResolver,
        bus: RxBus,
        prefs: Preferences,
        profile: ProfileFunction,
        profileU: ProfileUtil,
        constraints: ConstraintsChecker,
        loopRef: Loop,
        iobCob: IobCobCalculator,
        dates: DateUtil,
        cfg: Config,
        entryLogger: UserEntryLogger,
        automationRef: Automation,
        glucose: GlucoseStatusProvider,
        persistence: PersistenceLayer,
        deviceStatus: ProcessedDeviceStatusData,
        guard: RunningModeGuard,
        ch: ConcentrationHelper,
        executor: WizardBolusExecutor,
        scope: CoroutineScope
    ): BolusWizard = BolusWizard(
        logger, text, bus, prefs, profile, profileU, constraints, loopRef, iobCob, dates, cfg,
        entryLogger, automationRef, glucose, persistence, deviceStatus, guard, ch, executor, scope
    )
}
