package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.plugins.source.XdripPlugin
import info.nightscout.shared.interfaces.ResourceHelper
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class XdripPluginTest : TestBase() {

    private lateinit var xdripPlugin: XdripPlugin

    @Mock lateinit var rh: ResourceHelper

    @BeforeEach
    fun setup() {
        xdripPlugin = XdripPlugin(HasAndroidInjector { AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, xdripPlugin.advancedFilteringSupported())
    }
}