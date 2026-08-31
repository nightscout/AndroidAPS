package app.aaps

import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.e2e.RetryRule
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base for instrumented tests that need the app's plugins registered and its config initialised.
 *
 * Was `HiltInstrumentedTest`. It carried `HiltAndroidRule` and four `@Inject lateinit var` fields, plus
 * a `@HasMemberInjections` annotation that existed only to stop Metro's Dagger interop rejecting an
 * open class with `@Inject` fields. None of that is needed: [app.aaps.di.BaseTestApp] owns one graph
 * for the process, so the objects are simply read from it.
 *
 * The superclass `@Before` still runs before any subclass `@Before`, so subclasses may use these in
 * their own setup.
 */
abstract class AapsInstrumentedTest {

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    protected val pluginStore get() = testGraphs.pluginStore
    protected val configBuilder get() = testGraphs.configBuilder
    protected val pluginList get() = testGraphs.allPlugins(testGraphs.aapsLogger)

    @Before
    fun setUpGraph() {
        // What MainApp.onCreate does in production. BaseTestApp deliberately does not: a test that
        // wants a particular plugin set configures it first, and this runs after that.
        pluginStore.plugins = pluginList
        configBuilder.initialize()
    }
}
