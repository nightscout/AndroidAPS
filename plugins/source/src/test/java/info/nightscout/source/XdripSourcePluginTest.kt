package info.nightscout.source

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import info.nightscout.shared.interfaces.ResourceHelper
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
        assertThat(xdripSourcePlugin.advancedFilteringSupported()).isFalse()
    }
}
