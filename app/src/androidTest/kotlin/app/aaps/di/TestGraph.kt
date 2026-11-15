package app.aaps.di

import androidx.test.core.app.ApplicationProvider
import app.aaps.di.metro.MetroGraphs
import app.aaps.helpers.IntegrationWaits
import app.aaps.helpers.RxHelper
import app.aaps.plugins.aps.utils.StaticInjector

/**
 * The Metro root for the test process.
 */
val testGraphs: MetroGraphs get() = ApplicationProvider.getApplicationContext<BaseTestApp>().graphs

/**
 * Constructed here instead. They are **per test attempt**, not per process - `RxHelper` accumulates the
 * events it has seen - so a test makes one in `@Before` and keeps it in a `lateinit` field. Not `by lazy`:
 * `RetryRule` runs a failed test again on the same test object, and `ResetGraphRule` gives every attempt a
 * new graph. A helper kept from the first attempt listened on the old graph's bus, so every retry of
 * `LoopTest` waited 40 s for an event the new graph had sent. The same goes for [newIntegrationWaits] and
 * [newStaticInjector].
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
