package info.nightscout.androidaps.plugins.configBuilder

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class ConfigBuilderPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
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
        configBuilderPlugin = ConfigBuilderPlugin(injector, aapsLogger, rh, sp, RxBus(aapsSchedulers, aapsLogger), activePlugin, uel, pumpSync)
    }
}