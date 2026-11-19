package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NSClientSourcePluginTest : TestBase() {

    private lateinit var nsClientSourcePlugin: NSClientSourcePlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config

    @BeforeEach
    fun setup() {
        nsClientSourcePlugin = NSClientSourcePlugin(rh, aapsLogger, config, mock())
    }

    @Test
    fun `plugin is created`() {
        assertThat(nsClientSourcePlugin).isNotNull()
    }

    /** The default is decided when the plugin is built, so the config has to be set up first. */
    private fun pluginFor(client: Boolean, platform: AppPlatform): NSClientSourcePlugin {
        whenever(config.AAPSCLIENT).thenReturn(client)
        whenever(config.platform).thenReturn(platform)
        return NSClientSourcePlugin(rh, aapsLogger, config, mock())
    }

    /** A client has no other glucose source, on any platform. */
    @Test
    fun `nightscout is the default glucose source of a client`() {
        assertThat(pluginFor(client = true, platform = AppPlatform.Android).isDefault()).isTrue()
        assertThat(pluginFor(client = true, platform = AppPlatform.Ios).isDefault()).isTrue()
    }

    /** On Android the full app defaults to the Dexcom source, so Nightscout must not claim it as well. */
    @Test
    fun `nightscout is not the default of the full app on android`() {
        assertThat(pluginFor(client = false, platform = AppPlatform.Android).isDefault()).isFalse()
    }

    /**
     * Temporary, and pinned so that removing it is a decision: without it the iOS full app has no
     * default glucose source and does not start. See the comment on `setDefault` in the plugin.
     */
    @Test
    fun `nightscout is the default of the full app on ios for now`() {
        assertThat(pluginFor(client = false, platform = AppPlatform.Ios).isDefault()).isTrue()
    }

    /** Only iOS, so the temporary rule cannot quietly reach another platform. */
    @Test
    fun `the temporary rule does not reach a full desktop build`() {
        assertThat(pluginFor(client = false, platform = AppPlatform.Desktop).isDefault()).isFalse()
    }
}
