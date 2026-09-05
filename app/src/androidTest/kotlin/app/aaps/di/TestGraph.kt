package app.aaps.di

import androidx.test.core.app.ApplicationProvider
import app.aaps.di.testGraphs
import app.aaps.di.metro.MetroGraphs
import app.aaps.helpers.IntegrationWaits
import app.aaps.helpers.RxHelper
import app.aaps.plugins.aps.utils.StaticInjector

/**
 * The Metro root for the test process.
 */
val testGraphs: MetroGraphs get() = ApplicationProvider.getApplicationContext<BaseTestApp>().graphs

/**
 * Constructed here instead. They are **per test**, not per process - `RxHelper` accumulates the events
 * it has seen - so a test holds one in a `by lazy` field rather than calling this on every use.
 */
fun newRxHelper(): RxHelper = RxHelper(testGraphs.rxBus, testGraphs.dateUtil, testGraphs.aapsLogger)

fun newIntegrationWaits(): IntegrationWaits =
    IntegrationWaits(testGraphs.persistenceLayer, testGraphs.iobCobCalculator, testGraphs.aapsLogger)

/**
 * `StaticInjector` owns its own small Metro graph (`AlgTestGraph`), filled entirely from these - see
 * that class for why it builds nothing itself.
 */
fun newStaticInjector(): StaticInjector = with(testGraphs) {
    StaticInjector(
        aapsLogger, constraintsChecker, preferences, activePlugin, processedTbrEbData, profileFunction,
        resourceHelper, decimalFormatter, concentrationHelper, dateUtil, profileUtil
    )
}
