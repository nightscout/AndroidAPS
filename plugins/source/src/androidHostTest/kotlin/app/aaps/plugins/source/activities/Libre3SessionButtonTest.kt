package app.aaps.plugins.source.activities

import app.aaps.plugins.libre3.Libre3CgmDriver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * The Connect / Disconnect button of the Libre 3 status screen.
 *
 * The screen used to keep the driver it saw first. After a promotion or a plugin restart that
 * instance is retired, so Disconnect went to a driver that no longer runs the session.
 */
class Libre3SessionButtonTest {

    private val retired: Libre3CgmDriver = mock()
    private val current: Libre3CgmDriver = mock()

    @Test
    fun `disconnect goes to the driver of the moment of the tap`() {
        // The screen is drawn while the old driver is the production one ...
        var registry: Libre3CgmDriver = retired
        val currentDriver = { registry }
        // ... then a promotion hands out a new one before the tap.
        registry = current

        val action = libre3SessionButtonClick(
            sessionUp = true,
            pluginStopped = false,
            currentDriver = currentDriver,
            connect = {},
        )

        assertThat(action).isEqualTo(Libre3SessionButtonAction.DISCONNECTED)
        verify(current).disconnect()
        verify(retired, never()).disconnect()
    }

    @Test
    fun `connect is refused while the plugin is stopped`() {
        var connectAsked = false

        val action = libre3SessionButtonClick(
            sessionUp = false,
            pluginStopped = true,
            currentDriver = { current },
            connect = { connectAsked = true },
        )

        assertThat(action).isEqualTo(Libre3SessionButtonAction.REFUSED_PLUGIN_STOPPED)
        assertThat(connectAsked).isFalse()
    }

    @Test
    fun `connect goes through the plugin while it runs`() {
        var connectAsked = false

        val action = libre3SessionButtonClick(
            sessionUp = false,
            pluginStopped = false,
            currentDriver = { current },
            connect = { connectAsked = true },
        )

        assertThat(action).isEqualTo(Libre3SessionButtonAction.CONNECT_ASKED)
        assertThat(connectAsked).isTrue()
    }
}
