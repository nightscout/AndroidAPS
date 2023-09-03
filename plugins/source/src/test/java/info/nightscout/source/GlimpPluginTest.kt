package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class GlimpPluginTest : TestBase() {

    private lateinit var glimpPlugin: GlimpPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    @BeforeEach
    fun setup() {
        glimpPlugin = GlimpPlugin({ AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, glimpPlugin.advancedFilteringSupported())
    }
}