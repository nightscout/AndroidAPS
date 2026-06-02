package app.aaps

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base class for Hilt instrumented tests. Holds the [HiltAndroidRule], injects the test, and performs
 * the plugin/config initialization that [app.aaps.MainApp] does in onCreate (the test application can't,
 * since the Hilt component only exists once the rule has run). Concrete tests must still be annotated
 * `@HiltAndroidTest` and `@RunWith(AndroidJUnit4::class)`. The superclass `@Before` runs before any
 * subclass `@Before`, so subclasses may use injected dependencies in their own setup.
 */
abstract class HiltInstrumentedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

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
