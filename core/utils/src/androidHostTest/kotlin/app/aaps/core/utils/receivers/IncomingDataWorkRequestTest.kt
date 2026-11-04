package app.aaps.core.utils.receivers

import android.content.Context
import androidx.work.Data
import androidx.work.OutOfQuotaPolicy
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Incoming data that the loop waits for (a new BG, an SMS command) must not wait for the plain job
 * quota, which Android 16 counts even for an app with a foreground service.
 */
@Suppress("RestrictedApi")
class IncomingDataWorkRequestTest {

    private class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

        override fun doWork(): Result = Result.success()
    }

    @Test
    fun `the request is expedited and falls back to a plain job when that quota is used up`() {
        val request = incomingDataWorkRequest(NoopWorker::class.java)

        assertTrue(request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
        assertEquals(NoopWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun `the request carries its input data`() {
        val data = Data.Builder().putString("collection", "entries").build()

        val request = incomingDataWorkRequest(NoopWorker::class.java, data)

        assertEquals("entries", request.workSpec.input.getString("collection"))
    }
}
