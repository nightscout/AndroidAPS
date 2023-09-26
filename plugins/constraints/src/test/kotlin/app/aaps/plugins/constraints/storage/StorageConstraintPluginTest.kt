package app.aaps.plugins.constraints.storage

import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
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

    class MockedStorageConstraintPlugin(
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
        assertThat(mocked.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isFalse()
        // Set free space over 200(Mb) to enable loop
        mocked.memSize = 300L
        assertThat(mocked.isClosedLoopAllowed(ConstraintObject(true, aapsLogger)).value()).isTrue()
    }
}
