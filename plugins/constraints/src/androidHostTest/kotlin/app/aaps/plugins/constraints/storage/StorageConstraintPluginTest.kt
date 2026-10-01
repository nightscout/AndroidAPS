package app.aaps.plugins.constraints.storage

import app.aaps.plugins.constraints.ConstraintsStringsValues
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.whenever

class StorageConstraintPluginTest : TestBase() {

    private val rh = generatedTextResolver("constraints" to ConstraintsStringsValues::textOf)
    @Mock lateinit var notificationManager: NotificationManager

    private lateinit var storageConstraintPlugin: StorageConstraintPlugin

    @BeforeEach fun prepareMock() {
        storageConstraintPlugin = StorageConstraintPlugin(aapsLogger, rh, notificationManager)
    }

    class MockedStorageConstraintPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager
    ) : StorageConstraintPlugin(aapsLogger, rh, notificationManager) {

        var memSize = 150L
        override fun availableInternalMemorySize(): Long = memSize
    }

    @Test fun isLoopInvocationAllowedTest() = runTest {
        val mocked = MockedStorageConstraintPlugin(aapsLogger, rh, notificationManager)
        // Set free space under 200(Mb) to disable loop
        mocked.memSize = 150L
        assertThat(mocked.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isFalse()
        // Set free space over 200(Mb) to enable loop
        mocked.memSize = 300L
        assertThat(mocked.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isTrue()
    }
}
