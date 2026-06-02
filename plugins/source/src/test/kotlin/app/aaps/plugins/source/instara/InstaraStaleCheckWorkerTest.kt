package app.aaps.plugins.source.instara

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric test: InstaraStaleCheckWorker needs a real Android Context because its `finally`
 * reschedules via WorkManager.getInstance() and its request paths build & send android Intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstaraStaleCheckWorkerTest {

    private val application: Application get() = RuntimeEnvironment.getApplication()
    private val aapsLogger = AAPSLoggerTest()
    private val fabricPrivacy: FabricPrivacy = mock()
    private val persistenceLayer: PersistenceLayer = mock()
    private val preferences: Preferences = mock()

    private val instaraRequestAction = "info.nightscout.androidaps.action.REQUEST_Teljane_DATA"

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
    }

    private fun runWorker(): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<InstaraStaleCheckWorker>(application)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    InstaraStaleCheckWorker(appContext, workerParameters, aapsLogger, fabricPrivacy, persistenceLayer, preferences)
            })
            .build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun `disabled setting skips and sends no broadcast`() {
        whenever(preferences.get(InstaraBooleanKey.HistoryRequestEnabled)).thenReturn(false)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(shadowOf(application).broadcastIntents.isEmpty())
    }

    @Test
    fun `enabled with empty device meta requests history from sgvId 0`() {
        whenever(preferences.get(InstaraBooleanKey.HistoryRequestEnabled)).thenReturn(true)
        whenever(preferences.get(InstaraStringKey.DeviceMetaJson)).thenReturn("{}")

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val intents = shadowOf(application).broadcastIntents
        assertEquals(1, intents.size)
        assertEquals(instaraRequestAction, intents[0].action)
        assertTrue(intents[0].getStringExtra("data")!!.contains("\"sgvId\":\"0\""))
    }

    @Test
    fun `enabled with device meta but no stored values requests from sgvId 0`() {
        whenever(preferences.get(InstaraBooleanKey.HistoryRequestEnabled)).thenReturn(true)
        // devicePrefix = 5, deviceStart = 500000, sgvStart valid, sgvMark valid
        whenever(preferences.get(InstaraStringKey.DeviceMetaJson)).thenReturn("""{"5":{"sgvStart":500001,"sgvMark":100}}""")
        runBlocking {
            whenever(persistenceLayer.getGlucoseValuesByPumpIdRange(any(), any(), any())).thenReturn(emptyList<GV>())
        }

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val intents = shadowOf(application).broadcastIntents
        assertEquals(1, intents.size)
        assertEquals(instaraRequestAction, intents[0].action)
    }
}
