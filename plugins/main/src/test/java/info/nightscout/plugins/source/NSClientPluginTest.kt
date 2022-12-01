package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.Config
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientPluginTest : TestBase() {

    private lateinit var nsClientSourcePlugin: info.nightscout.plugins.source.NSClientSourcePlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var config: Config

    @BeforeEach
    fun setup() {
        nsClientSourcePlugin = info.nightscout.plugins.source.NSClientSourcePlugin({ AndroidInjector { } }, rh, aapsLogger, config)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, nsClientSourcePlugin.advancedFilteringSupported())
    }
}