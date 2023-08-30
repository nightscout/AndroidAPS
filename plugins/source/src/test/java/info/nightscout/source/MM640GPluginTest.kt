package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.shared.interfaces.ResourceHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var rh: ResourceHelper

    @BeforeEach
    fun setup() {
        mM640gPlugin = MM640gPlugin({ AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, mM640gPlugin.advancedFilteringSupported())
    }
}