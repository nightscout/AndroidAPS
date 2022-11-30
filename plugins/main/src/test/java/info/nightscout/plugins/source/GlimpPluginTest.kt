package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.plugins.source.GlimpPlugin
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class GlimpPluginTest : TestBase() {

    private lateinit var glimpPlugin: GlimpPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    @BeforeEach
    fun setup() {
        glimpPlugin = GlimpPlugin(HasAndroidInjector { AndroidInjector { } }, rh, aapsLogger, sp)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, glimpPlugin.advancedFilteringSupported())
    }
}