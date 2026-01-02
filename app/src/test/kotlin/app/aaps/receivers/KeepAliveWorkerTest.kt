package app.aaps.receivers

import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.keys.LongNonKey
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KeepAliveWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: KeepAliveWorker

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var maintenancePlugin: MaintenancePlugin
    @Mock private lateinit var dstHelperPlugin: DstHelperPlugin
    @Mock private lateinit var workerParameters: WorkerParameters
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var ads: AutosensDataStore
    @Mock private lateinit var localAlertUtils: LocalAlertUtils
    @Mock private lateinit var workManager: WorkManager
    @Mock private lateinit var listenableFuture: ListenableFuture<List<WorkInfo>>
    @Mock private lateinit var mockedRxBus: RxBus

    @BeforeEach
    fun setUp() {
        // Configure mocks provided by the base class or declared here.
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(workManager.getWorkInfos(any())).thenReturn(listenableFuture)
        whenever(listenableFuture.get()).thenReturn(emptyList())
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to "KA_5"))
    }

    // Helper to create the worker instance directly
    private fun createWorker(): KeepAliveWorker =
        KeepAliveWorker(context, workerParameters).also {
            // Manually inject all mocks.
            it.persistenceLayer = persistenceLayer
            it.config = config
            it.iobCobCalculator = iobCobCalculator
            it.loop = loop
            it.dateUtil = dateUtil
            it.activePlugin = activePlugin
            it.profileFunction = profileFunction
            it.rxBus = mockedRxBus
            it.commandQueue = commandQueue
            it.maintenancePlugin = maintenancePlugin
            it.preferences = preferences
            it.dstHelperPlugin = dstHelperPlugin
            it.aapsLogger = aapsLogger
            it.localAlertUtils = localAlertUtils
            it.workManager = workManager
            it.rh = rh
        }

    @Test
    fun `checkPump requests status when connection is outdated`() = runBlocking {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        // Act
        worker.checkPump()

        // Assert
        verify(commandQueue).readStatus(anyOrNull(), anyOrNull())
        Unit
    }

    @Test
    fun `checkPump sends profile switch event if profile is mismatched`() = runBlocking {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        testPumpPlugin.isProfileSet = false

        // Act
        worker.checkPump()

        // Assert
        verify(mockedRxBus).send(any<EventProfileSwitchChanged>())
    }

    @Test
    fun `checkPump does nothing if mode is DISCONNECTED_PUMP`() = runBlocking {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode).thenReturn(RM.Mode.DISCONNECTED_PUMP)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(commandQueue, never()).readStatus(any(), anyOrNull())
        verify(mockedRxBus, never()).send(any<EventProfileSwitchChanged>())
    }

    @Test
    fun `checkAPS schedules device status upload if BG is missing`() = runBlocking {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(null)

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `databaseCleanup does NOT run if it was run less than a day ago`() = runBlocking {
        // Arrange
        worker = createWorker()
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.hours(12).msecs())

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(persistenceLayer, never()).cleanupDatabase(any(), any())
        Unit
    }
}
