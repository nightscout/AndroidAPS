package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.shared.interfaces.ResourceHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class XdripPluginTest : TestBase() {

    private lateinit var xdripPlugin: XdripPlugin

    @Mock lateinit var rh: ResourceHelper

    @BeforeEach
    fun setup() {
        xdripPlugin = XdripPlugin({ AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, xdripPlugin.advancedFilteringSupported())
    }
}