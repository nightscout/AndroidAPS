package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    @BeforeEach
    fun setup() {
        mM640gPlugin = MM640gPlugin({ AndroidInjector { } }, rh, aapsLogger, sp)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, mM640gPlugin.advancedFilteringSupported())
    }
}