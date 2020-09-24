package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var resourceHelper: ResourceHelper

    @Before
    fun setup() {
        mM640gPlugin = MM640gPlugin(HasAndroidInjector { AndroidInjector { } }, resourceHelper, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, mM640gPlugin.advancedFilteringSupported())
    }
}