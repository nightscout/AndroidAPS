package app.aaps.di

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
import app.aaps.di.metro.MetroGraphs
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
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * :app is the one module that can never become multiplatform, so wiring put here never has to move
 * again; :implementation would probably also be safe, being the ANDROID implementation of
 * :core:interfaces, but that is a bet on the future and this needs no bet.
 *
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
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

    /*
     * Built by Metro now - see CoreObjectsGraph in :core:objects commonMain, which compiles for iOS.
     * These delegate so Dagger consumers keep working and there is exactly one instance.
     * CryptoUtil above is NOT delegated: its class is androidMain, so it cannot be in the graph.
     */
    @Provides @Singleton fun provideRunningModeGuard(graphs: MetroGraphs): RunningModeGuard = graphs.runningModeGuard

    @Provides @Singleton fun provideQuickWizard(graphs: MetroGraphs): QuickWizard = graphs.quickWizard

    @Provides fun provideBolusWizard(graphs: MetroGraphs): BolusWizard = graphs.bolusWizard
}
