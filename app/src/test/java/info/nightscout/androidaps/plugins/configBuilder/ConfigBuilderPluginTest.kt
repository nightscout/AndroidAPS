package info.nightscout.androidaps.plugins.configBuilder

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
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

    @Mock lateinit var sp: SP
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var pumpSync: PumpSync

    private lateinit var configBuilderPlugin: ConfigBuilderPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test
    fun dummy() {

    }

    @Before
    fun prepareMock() {
        configBuilderPlugin = ConfigBuilderPlugin(injector, aapsLogger, resourceHelper, sp, RxBusWrapper(aapsSchedulers), activePlugin, uel, pumpSync)
    }
}