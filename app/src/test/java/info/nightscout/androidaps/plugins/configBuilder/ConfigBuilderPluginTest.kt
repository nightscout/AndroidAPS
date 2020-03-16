package info.nightscout.androidaps.plugins.configBuilder

import dagger.Lazy
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.TestBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSProfilePlugin::class)
class ConfigBuilderPluginTest : TestBase() {

    @Mock lateinit var insulinOrefRapidActingPlugin: Lazy<InsulinOrefRapidActingPlugin>
    @Mock lateinit var localProfilePlugin: Lazy<LocalProfilePlugin>
    @Mock lateinit var virtualPumpPlugin: Lazy<VirtualPumpPlugin>
    @Mock lateinit var treatmentsPlugin: Lazy<TreatmentsPlugin>
    @Mock lateinit var sensitivityOref0Plugin: Lazy<SensitivityOref0Plugin>
    @Mock lateinit var sensitivityOref1Plugin: Lazy<SensitivityOref1Plugin>

    @Mock lateinit var sp: SP
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var activePlugin: ActivePluginProvider

    lateinit var configBuilderPlugin: ConfigBuilderPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {

        }
    }

    @Test
    fun dummy() {

    }

    @Before
    fun prepareMock() {
        configBuilderPlugin = ConfigBuilderPlugin(activePlugin, injector, sp, RxBusWrapper(), aapsLogger, resourceHelper, commandQueue)
    }
}