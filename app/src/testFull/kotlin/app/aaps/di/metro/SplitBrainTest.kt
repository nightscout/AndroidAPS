package app.aaps.di.metro

import app.aaps.plugins.sync.tidepool.TidepoolPlugin
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.database.AppRepository
import app.aaps.database.persistence.PersistenceLayerImpl
import app.aaps.implementation.androidNotification.AlarmSoundPlayerImpl
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderImpl
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.source.AidexPlugin
import app.aaps.pump.common.compose.RileyLinkPairWizardViewModel
import app.aaps.pump.dana.compose.DanaHistoryViewModel
import app.aaps.pump.danar.compose.DanaRPairWizardViewModel
import app.aaps.pump.danars.compose.DanaRSOverviewViewModel
import app.aaps.pump.diaconn.compose.DiaconnHistoryViewModel
import app.aaps.pump.eopatch.compose.EopatchOverviewViewModel
import app.aaps.pump.equil.compose.EquilHistoryViewModel
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.medtrum.compose.MedtrumOverviewViewModel
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.ui.compose.calibrationDialog.CalibrationDialogViewModel
import app.aaps.workflow.CalculationWorkflowImpl
import info.nightscout.pump.combov2.ComboV2Plugin
import app.aaps.shared.tests.unbridgedSingletons
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * No class is built by both frameworks at once.
 *
 * `PumpLeavesTest` asks this question about pump view models. This asks it about everything, because
 * the same mistake is possible anywhere: a class carrying javax `@Singleton` is buildable by Dagger and,
 * since the graph in `:app` runs without Dagger interop, by Metro too - which ignores the javax scope and
 * builds a fresh one per injection point. Both halves look correct and the build passes; the object just
 * stops being shared.
 *
 * Three of these shipped in a single day before this test existed, each found by hand:
 * `ProfileSwitchSilentGate` (a scene profile switch showed the notification its gate suppresses),
 * `ReceiverDelegate` (Tidepool read an upload gate nothing updated) and `RateLimit` (nothing was ever
 * rate limited). None of them failed a build, a unit test or CI.
 *
 * To fix a report, name an owner - `@SingleIn` plus a `CoreObjectsModule` delegate so Dagger borrows
 * Metro's, or leave it Dagger's and add a leaf so Metro borrows Dagger's. Do not add it to an ignore
 * list unless the class genuinely holds no state, and say why.
 */
class SplitBrainTest {

    @Test
    fun `nothing javax-scoped is rebuilt by Metro`() {
        val reports = unbridgedSingletons(anchors = ANCHORS, daggerOwned = daggerOwnedTypes())
            .filterNot { report -> STATELESS.any { report.startsWith(it) } }

        assertWithMessage(
            "These are javax @Singleton, so exactly one was intended, but Metro builds its own copy. " +
                "Give the class @SingleIn and a CoreObjectsModule delegate, or hand it over with a leaf."
        ).that(reports).isEmpty()
    }

    /**
     * Classes a second copy of does no harm, each read before being listed: they take their dependencies
     * in the constructor and compute, or hold only a cache or a constant table, so two instances behave
     * the same.
     *
     * A duplicate is still a wasted allocation, so this is a tolerance rather than an endorsement - but
     * scoping them would add a delegate that carries no meaning. **Only add an entry after reading the
     * class**: "looks harmless" is exactly how the three real ones got through, and a script that tried
     * to decide this automatically called every candidate stateless - including AutotuneIob and AutotuneFS,
     * which turned out to be the pair that made autotune read data it had never populated.
     */
    private val STATELESS = listOf(
        // Branches on config and forwards to the dispatcher. No fields.
        "app.aaps.implementation.bolus.RoleBranch",
        // Encrypts and decrypts what it is handed; every var in it is a local.
        "app.aaps.implementation.maintenance.formats.EncryptedPrefsFormat",
        // Builds upload payloads from its arguments. No fields.
        "app.aaps.plugins.sync.tidepool.comm.UploadChunk",
        // Holds one Intent built from Context in its constructor; two are interchangeable.
        "app.aaps.persistentNotification.DummyServiceHelper",
        // Pure calculation over its arguments. No fields.
        "app.aaps.ui.compose.navigation.ElementAvailability",
        "app.aaps.ui.compose.quickLaunch.QuickLaunchResolver",
        "app.aaps.ui.search.WikiSearchRepository",
        // Its only `var` is a constructor parameter, not state.
        "app.aaps.ui.activityMonitor.ActivityMonitor",
        // Constant lookup tables, filled the same way in every instance.
        "app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfile",
        "app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfileDPV",
        // Caches the built index. A second copy rebuilds it - wasteful, not wrong.
        "app.aaps.ui.search.SearchIndexBuilder"
    )

    /**
     * One class per compiled output to scan - a module is invisible to this test unless something in it
     * is named here, so **add an anchor when a new module starts contributing to the graph**. Which class
     * does not matter; it is only used to find that module's compiled output.
     *
     * Every module that carries a Metro contribution is listed. The scan itself filters to `app.aaps.*`
     * and `info.nightscout.*`, so pulling in a module costs only the walk over its own classes.
     */
    private val ANCHORS
        get() = listOf(
            AapsLeaves::class.java,                          // :app
            AlarmSoundPlayerImpl::class.java,                // :implementation
            PersistenceLayerImpl::class.java,               // :database:persistence
            AppRepository::class.java,                      // :database:impl
            CalibrationDialogViewModel::class.java,          // :ui
            TidepoolPlugin::class.java,                      // :plugins:sync
            BgQualityCheckPlugin::class.java,                // :plugins:constraints
            AidexPlugin::class.java,                         // :plugins:source
            AutotunePlugin::class.java,                      // :plugins:aps
            AvgSmoothingPlugin::class.java,                  // :plugins:smoothing
            SensitivityAAPSPlugin::class.java,               // :plugins:sensitivity
            LinearCalibrationPlugin::class.java,             // :plugins:calibration
            ConfigBuilderImpl::class.java,                   // :plugins:configuration
            LastLocationDataContainer::class.java,           // :plugins:automation
            CalculationWorkflowImpl::class.java,             // :workflow
            VirtualPumpPlugin::class.java,                   // :pump:virtual
            MedtronicPumpPlugin::class.java,                 // :pump:medtronic
            RileyLinkPairWizardViewModel::class.java,        // :pump:rileylink
            EopatchOverviewViewModel::class.java,            // :pump:eopatch
            InsightPlugin::class.java,                       // :pump:insight
            OmnipodDashPumpPlugin::class.java,               // :pump:omnipod:dash
            DiaconnHistoryViewModel::class.java,             // :pump:diaconn
            EquilHistoryViewModel::class.java,               // :pump:equil
            MedtrumOverviewViewModel::class.java,            // :pump:medtrum
            ComboV2Plugin::class.java,                       // :pump:combov2
            DanaHistoryViewModel::class.java,                // :pump:dana
            DanaRPairWizardViewModel::class.java,            // :pump:danar
            DanaRSOverviewViewModel::class.java              // :pump:danars
        )

    /**
     * The types Dagger owns and lends to Metro, read from the leaves themselves rather than listed here
     * - a hand-written copy would drift the moment someone adds one.
     *
     * `CoreObjectsModule`'s `provide*(graphs: MetroGraphs)` delegates are deliberately **not** included.
     * They run the other way, and a delegate alone does not make a type safe: it returns whatever Metro
     * built at that moment, so an unscoped Metro side still hands every other Metro consumer its own
     * copy while Dagger caches one. Such a class must also carry `@SingleIn`, and letting the delegate
     * excuse it is exactly the hole that let this test pass over a reverted ProfileSwitchSilentGate.
     */
    private fun daggerOwnedTypes(): Set<Class<*>> {
        val leaves = (AapsLeaves::class.java.declaredMethods + PumpLeaves::class.java.declaredMethods)
            .filter { it.parameterCount == 0 }
            .map { it.returnType }
            .toSet()

        check(leaves.size > 50) { "Only ${leaves.size} leaf types found - the reflection broke" }
        return leaves
    }
}
