package app.aaps.di

import androidx.test.core.app.ApplicationProvider
import app.aaps.di.testGraphs
import app.aaps.di.metro.MetroGraphs
import app.aaps.helpers.IntegrationWaits
import app.aaps.helpers.RxHelper
import app.aaps.plugins.aps.utils.StaticInjector

/**
 * The Metro root for the test process.
 *
 * Instrumented tests used to get their objects as `@Inject lateinit var` fields, filled by
 * `HiltAndroidRule` from a component built per test. There is no Hilt now: `AapsTestRunner` installs
 * [BaseTestApp], which owns one graph for the whole process, and a test reads what it needs from here.
 *
 * Reading rather than injecting also closed a real hole. Dagger does not understand Metro's
 * `@SingleIn`, so an `@Inject` field of a Metro-owned class made Hilt build the test its **own second
 * copy** - the pump emulator tests were driving plugin objects the running app had never heard of.
 */
val testGraphs: MetroGraphs get() = ApplicationProvider.getApplicationContext<BaseTestApp>().graphs

/**
 * The androidTest-only helpers, which Hilt used to build from their `@Inject` constructors.
 *
 * Constructed here instead. They are **per test**, not per process - `RxHelper` accumulates the events
 * it has seen - so a test holds one in a `by lazy` field rather than calling this on every use.
 */
fun newRxHelper(): RxHelper = RxHelper(testGraphs.rxBus, testGraphs.dateUtil, testGraphs.aapsLogger)

fun newIntegrationWaits(): IntegrationWaits =
    IntegrationWaits(testGraphs.persistenceLayer, testGraphs.iobCobCalculator, testGraphs.aapsLogger)

/**
 * The algorithm-replay member injector, built from the same objects Hilt used to hand it.
 *
 * `StaticInjector` owns its own small Metro graph (`AlgTestGraph`), filled entirely from these - see
 * that class for why it builds nothing itself.
 */
fun newStaticInjector(): StaticInjector = with(testGraphs) {
    StaticInjector(
        aapsLogger, constraintsChecker, preferences, activePlugin, processedTbrEbData, profileFunction,
        resourceHelper, decimalFormatter, concentrationHelper, dateUtil, profileUtil
    )
}
