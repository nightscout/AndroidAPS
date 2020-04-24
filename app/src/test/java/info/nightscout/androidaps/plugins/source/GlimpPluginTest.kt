package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class GlimpPluginTest : TestBase() {
    private lateinit var glimpPlugin: GlimpPlugin

    @Mock lateinit var resourceHelper: ResourceHelper

    @Before
    fun setup() {
        glimpPlugin = GlimpPlugin(HasAndroidInjector { AndroidInjector { } }, resourceHelper, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, glimpPlugin.advancedFilteringSupported())
    }
}