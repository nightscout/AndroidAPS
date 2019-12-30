package info.nightscout.androidaps.plugins.source

import info.nightscout.androidaps.logging.AAPSLogger
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

class GlimpPluginTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var glimpPlugin: GlimpPlugin;

    @Mock lateinit var aapsLogger: AAPSLogger

    @Before
    fun setup() {
        glimpPlugin = GlimpPlugin(aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, glimpPlugin.advancedFilteringSupported())
    }
}