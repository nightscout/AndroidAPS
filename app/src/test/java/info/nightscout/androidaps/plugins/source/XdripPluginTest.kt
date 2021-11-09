package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class XdripPluginTest : TestBase() {

    private lateinit var xdripPlugin: XdripPlugin

    @Mock lateinit var rh: ResourceHelper

    @Before
    fun setup() {
        xdripPlugin = XdripPlugin(HasAndroidInjector { AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, xdripPlugin.advancedFilteringSupported())
    }
}