package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.Config
import info.nightscout.shared.interfaces.ResourceHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientPluginTest : TestBase() {

    private lateinit var nsClientSourcePlugin: NSClientSourcePlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config

    @BeforeEach
    fun setup() {
        nsClientSourcePlugin = NSClientSourcePlugin({ AndroidInjector { } }, rh, aapsLogger, config)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, nsClientSourcePlugin.advancedFilteringSupported())
    }
}