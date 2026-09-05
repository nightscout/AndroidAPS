package app.aaps.plugins.sync.nsclientV3.ws

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The websocket connection lifecycle, which had no tests before it was pulled out of the plugin.
 *
 * It was not testable there: `startService` and `stopService` were private, and the plugin tests
 * assigned the service field directly, so bind and unbind never ran. The behaviours below are the
 * ones with a comment in the source explaining why they are the way they are - which is exactly the
 * set that a refactor can quietly break.
 */
class ServiceNsConnectionTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var service: NSClientV3Service
    @Mock lateinit var preferences: Preferences

    private lateinit var sut: ServiceNsConnection

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        sut = ServiceNsConnection(context, aapsLogger, preferences)
    }

    /** Binds, then drives the bind callback the way Android would, and hands back the connection. */
    private fun bindAndConnect(): ServiceConnection {
        sut.start("test")
        val captor = ArgumentCaptor.forClass(ServiceConnection::class.java)
        verify(context).bindService(any<Intent>(), captor.capture(), anyInt())
        val connection = captor.value
        connection.onServiceConnected(ComponentName("pkg", "cls"), NSClientV3Service.LocalBinder(service))
        return connection
    }

    @Test
    fun `start binds the service when websockets are enabled`() {
        sut.start("test")

        verify(context).bindService(any<Intent>(), any<ServiceConnection>(), anyInt())
    }

    @Test
    fun `start does nothing when websockets are switched off`() {
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)

        sut.start("test")

        verify(context, never()).bindService(any<Intent>(), any<ServiceConnection>(), anyInt())
    }

    /**
     * Android reuses an already-alive service on rebind: onCreate does not fire but the bind
     * callback does. Both paths ask for the sockets, so asking twice must be harmless.
     */
    @Test
    fun `connecting the service asks it to open its sockets`() {
        bindAndConnect()

        verify(service).initializeWebSockets("serviceConnected")
    }

    @Test
    fun `starting again while bound re-checks the sockets instead of binding a second time`() {
        bindAndConnect()

        sut.start("setClient")

        verify(service).initializeWebSockets("setClient")
        // Once for the original bind, never a second time.
        verify(context).bindService(any<Intent>(), any<ServiceConnection>(), anyInt())
    }

    /**
     * The sockets must come down before the service is released. A quick restart otherwise races
     * the asynchronous onDestroy and finds the old sockets still attached.
     */
    @Test
    fun `stop closes the sockets before releasing the service`() {
        bindAndConnect()

        sut.stop()

        val order = inOrder(service, context)
        order.verify(service).shutdownWebsockets()
        order.verify(context).unbindService(any<ServiceConnection>())
    }

    @Test
    fun `stop on a connection that was never started does nothing`() {
        sut.stop()

        verify(context, never()).unbindService(any<ServiceConnection>())
    }

    /**
     * Process death skips shutdownWebsockets, so without this the UI would keep showing "connected"
     * until something rebound.
     */
    @Test
    fun `losing the service reports not connected`() {
        val connection = bindAndConnect()
        sut.setConnected(true)
        assertThat(sut.connected.value).isTrue()

        connection.onServiceDisconnected(ComponentName("pkg", "cls"))

        assertThat(sut.connected.value).isFalse()
    }

    /**
     * Three-valued on purpose: "nothing is carrying a connection" is not the same as "carrying one
     * that is not connected", and the status line renders them differently.
     */
    @Test
    fun `socketConnected is null until something is carrying the connection`() {
        assertThat(sut.socketConnected).isNull()

        bindAndConnect()
        assertThat(sut.socketConnected).isFalse()

        sut.setConnected(true)
        assertThat(sut.socketConnected).isTrue()
    }

    @Test
    fun `hasLiveSocket follows the storage socket`() {
        assertThat(sut.hasLiveSocket).isFalse()

        bindAndConnect()
        whenever(service.storageSocket).thenReturn(null)
        assertThat(sut.hasLiveSocket).isFalse()

        whenever(service.storageSocket).thenReturn(mockSocket())
        assertThat(sut.hasLiveSocket).isTrue()
    }

    @Test
    fun `acknowledging an alarm passes it to the service`() {
        val alarm = org.mockito.kotlin.mock<NSAlarm>()
        bindAndConnect()

        sut.acknowledgeAlarm(alarm, 600_000L)

        verify(service).handleClearAlarm(eq(alarm), eq(600_000L))
    }

    @Test
    fun `acknowledging an alarm with nothing bound is ignored`() {
        sut.acknowledgeAlarm(org.mockito.kotlin.mock(), 600_000L)

        verify(service, never()).handleClearAlarm(any(), any())
    }

    private fun mockSocket(): NsSocket = org.mockito.kotlin.mock()
}
