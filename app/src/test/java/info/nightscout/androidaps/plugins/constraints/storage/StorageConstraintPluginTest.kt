package info.nightscout.androidaps.plugins.constraints.storage

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong

class StorageConstraintPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    private val rxBusWrapper = RxBus(aapsSchedulers, aapsLogger)

    private lateinit var storageConstraintPlugin: StorageConstraintPlugin

    @Before fun prepareMock() {
        storageConstraintPlugin = StorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, rh, rxBusWrapper)
        `when`(rh.gs(anyInt(), anyLong())).thenReturn("")
    }

    class MockedStorageConstraintPlugin constructor(
        injector: HasAndroidInjector,
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        rxBus: RxBus
    ) : StorageConstraintPlugin(injector, aapsLogger, rh, rxBus) {

        var memSize = 150L
        override fun availableInternalMemorySize(): Long = memSize
    }

    @Test fun isLoopInvocationAllowedTest() {
        val mocked = MockedStorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, rh, rxBusWrapper)
        // Set free space under 200(Mb) to disable loop
        mocked.memSize = 150L
        Assert.assertEquals(false, mocked.isClosedLoopAllowed(Constraint(true)).value())
        // Set free space over 200(Mb) to enable loop
        mocked.memSize = 300L
        Assert.assertEquals(true, mocked.isClosedLoopAllowed(Constraint(true)).value())
    }
}