package info.nightscout.source

import dagger.android.AndroidInjector
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class XdripSourcePluginTest : TestBase() {

    private lateinit var xdripSourcePlugin: XdripSourcePlugin

    @Mock lateinit var rh: ResourceHelper

    @BeforeEach
    fun setup() {
        xdripSourcePlugin = XdripSourcePlugin({ AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assertions.assertEquals(false, xdripSourcePlugin.advancedFilteringSupported())
    }
}