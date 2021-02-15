package info.nightscout.androidaps.plugins.configBuilder

import dagger.Lazy
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
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
@PrepareForTest(UserEntryLogger::class)
class ConfigBuilderPluginTest : TestBase() {

    @Mock lateinit var virtualPumpPlugin: Lazy<VirtualPumpPlugin>
    @Mock lateinit var treatmentsPlugin: Lazy<TreatmentsPlugin>

    @Mock lateinit var sp: SP
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var configBuilderPlugin: ConfigBuilderPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {

        }
    }

    @Test
    fun dummy() {

    }

    @Before
    fun prepareMock() {
        configBuilderPlugin = ConfigBuilderPlugin(injector, aapsLogger, resourceHelper, sp, RxBusWrapper(aapsSchedulers), activePlugin, uel)
    }
}