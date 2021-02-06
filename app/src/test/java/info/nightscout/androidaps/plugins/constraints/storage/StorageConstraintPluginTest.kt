package info.nightscout.androidaps.plugins.constraints.storage

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class StorageConstraintPluginTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    private val rxBusWrapper = RxBusWrapper(aapsSchedulers)

    private lateinit var storageConstraintPlugin: StorageConstraintPlugin

    @Before fun prepareMock() {
        storageConstraintPlugin = StorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, resourceHelper, rxBusWrapper)
    }

    class MockedStorageConstraintPlugin constructor(
        injector: HasAndroidInjector,
        aapsLogger: AAPSLogger,
        resourceHelper: ResourceHelper,
        rxBus: RxBusWrapper
    ) : StorageConstraintPlugin(injector, aapsLogger, resourceHelper, rxBus) {

        var memSize = 150L
        override fun availableInternalMemorySize(): Long = memSize
    }

    @Test fun isLoopInvocationAllowedTest() {
        val mocked = MockedStorageConstraintPlugin({ AndroidInjector { } }, aapsLogger, resourceHelper, rxBusWrapper)
        // Set free space under 200(Mb) to disable loop
        mocked.memSize = 150L
        Assert.assertEquals(false, mocked.isClosedLoopAllowed(Constraint(true)).value())
        // Set free space over 200(Mb) to enable loop
        mocked.memSize = 300L
        Assert.assertEquals(true, mocked.isClosedLoopAllowed(Constraint(true)).value())
    }
}