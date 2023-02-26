package info.nightscout.androidaps.plugins.configBuilder

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.configuration.configBuilder.ConfigBuilderPlugin
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class ConfigBuilderPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var configBuilderPlugin: ConfigBuilderPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test
    fun dummy() {

    }

    @BeforeEach
    fun prepareMock() {
        configBuilderPlugin = ConfigBuilderPlugin(injector, aapsLogger, rh, sp, RxBus(aapsSchedulers, aapsLogger), activePlugin, uel, pumpSync, protectionCheck, uiInteraction)
    }
}