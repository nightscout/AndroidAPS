package info.nightscout.plugins.constraints.storage

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.`when`

class StorageConstraintPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var storageConstraintPlugin: StorageConstraintPlugin

    @BeforeEach fun prepareMock() {
        storageConstraintPlugin = StorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, rh, uiInteraction)
        `when`(rh.gs(anyInt(), anyLong())).thenReturn("")
    }

    class MockedStorageConstraintPlugin constructor(
        injector: HasAndroidInjector,
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        uiInteraction: UiInteraction
    ) : StorageConstraintPlugin(injector, aapsLogger, rh, uiInteraction) {

        var memSize = 150L
        override fun availableInternalMemorySize(): Long = memSize
    }

    @Test fun isLoopInvocationAllowedTest() {
        val mocked = MockedStorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, rh, uiInteraction)
        // Set free space under 200(Mb) to disable loop
        mocked.memSize = 150L
        Assertions.assertEquals(false, mocked.isClosedLoopAllowed(Constraint(true)).value())
        // Set free space over 200(Mb) to enable loop
        mocked.memSize = 300L
        Assertions.assertEquals(true, mocked.isClosedLoopAllowed(Constraint(true)).value())
    }
}