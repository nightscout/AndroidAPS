package app.aaps

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.implementation.plugin.PluginStore
import app.aaps.e2e.RetryRule
import app.aaps.plugins.aps.utils.StaticInjector
import dagger.hilt.android.testing.HiltAndroidRule
import dev.zacsweers.metro.HasMemberInjections
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import javax.inject.Inject

/**
 * Base class for Hilt instrumented tests. Holds the [HiltAndroidRule], injects the test, and performs
 * the plugin/config initialization that [app.aaps.MainApp] does in onCreate (the test application can't,
 * since the Hilt component only exists once the rule has run). Concrete tests must still be annotated
 * `@HiltAndroidTest` and `@RunWith(AndroidJUnit4::class)`. The superclass `@Before` runs before any
 * subclass `@Before`, so subclasses may use injected dependencies in their own setup.
 *
 * These are integration tests that drive real WorkManager / CommandQueue / pump plumbing and poll for
 * side effects with wall-clock budgets, so they flake under CI load exactly like the e2e tests do —
 * see [RunningModeReconcilerIntegrationTest] failing intermittently on unrelated commits (CI builds
 * 40530 and 40565, both self-healing on a re-run of the identical SHA). [RetryRule] is therefore wired
 * here, as the outermost rule, so each attempt is a fully fresh test including Hilt setup/teardown.
 * The shard steps in `.circleci/config.yml` deliberately do NOT retry a `FAILURES!!!` run because they
 * assume this in-process retry already happened — before this rule that assumption was false for every
 * subclass of this base, which is why a single flaky assertion turned the whole job red.
 */
// Hilt injects these fields, not Metro. The annotation is only here because Metro's Dagger interop
// reads this source set too, and it refuses an open class with @Inject fields unless it is told the
// class really is a member-injection target.
@HasMemberInjections
abstract class HiltInstrumentedTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder

    // Not used directly; injected only to force StaticInjector initialization, as MainApp does.
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    @Before
    fun setUpHiltGraph() {
        hiltRule.inject()
        pluginStore.plugins = pluginList
        configBuilder.initialize()
    }
}
