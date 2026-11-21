package app.aaps.plugins.constraints.storage

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.constraints.ConstraintsStringsValues
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails

class StorageConstraintPluginTest : TestBase() {

    private val rh = generatedTextResolver("constraints" to ConstraintsStringsValues::textOf)
    @Mock lateinit var notificationManager: NotificationManager

    /** The free space the plugin sees, in MB. */
    private var freeMb = 150L
    private val freeStorage = object : FreeStorage {
        override fun availableMb(): Long = freeMb
    }

    private lateinit var plugin: StorageConstraintPlugin

    @BeforeEach fun prepareMock() {
        plugin = StorageConstraintPlugin(aapsLogger, rh, notificationManager, freeStorage)
    }

    @Test fun isLoopInvocationAllowedTest() = runTest {
        // Set free space under 200(Mb) to disable loop
        freeMb = 150L
        assertThat(plugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isFalse()
        // Set free space over 200(Mb) to enable loop
        freeMb = 300L
        assertThat(plugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isTrue()
    }

    @Test fun `exactly the minimum free space still allows the closed loop`() = runTest {
        freeMb = 200L

        assertThat(plugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isTrue()
        assertThat(postedNotifications()).isEmpty()
    }

    @Test fun `a full disk says why and tells the user`() = runTest {
        freeMb = 150L

        val allowed = plugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))

        assertThat(allowed.getReasons()).contains("Free at least 200 MB from internal storage! Loop disabled!")
        assertThat(postedNotifications()).containsExactly(NotificationId.DISK_FULL)
    }

    private fun postedNotifications(): List<NotificationId> =
        mockingDetails(notificationManager).invocations.filter { it.method.name == "post" }.map { it.arguments[0] as NotificationId }
}
