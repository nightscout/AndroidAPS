package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class NSClientPluginTest : TestBase() {

    private lateinit var nsClientSourcePlugin: NSClientSourcePlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var config: Config

    @Before
    fun setup() {
        nsClientSourcePlugin = NSClientSourcePlugin({ AndroidInjector { } }, rh, aapsLogger, config)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, nsClientSourcePlugin.advancedFilteringSupported())
    }
}