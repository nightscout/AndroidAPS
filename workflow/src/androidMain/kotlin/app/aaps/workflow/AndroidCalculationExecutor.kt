package app.aaps.workflow

import android.content.Context
import android.os.PowerManager
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/**
 * The calculation executor on Android: the coroutine executor every platform uses, plus a partial
 * wake lock for each run.
 *
 * It used to run the phases as WorkManager workers. See [CalculationExecutor] for why that stopped
 * the loop on Android 16. While the foreground service runs, it keeps this process alive, so a
 * coroutine needs nothing from the job scheduler - only the wake lock that the system used to hold
 * for a job.
 *
 * The runners come as providers for the cycle explained in [LazyCalculationExecutor].
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidCalculationExecutor(
    context: Context,
    aapsLogger: AAPSLogger,
    @ApplicationScope scope: CoroutineScope,
    prepare: () -> PrepareGraphDataRunner,
    post: () -> PostCalculationRunner
) : CalculationExecutor by LazyCalculationExecutor(scope, aapsLogger, prepare, post, AndroidCalculationWakeLock(context))

/** [CalculationWakeLock] on Android: a partial wake lock, so a run finishes with the screen off. */
internal class AndroidCalculationWakeLock(private val context: Context) : CalculationWakeLock {

    override fun acquire(timeoutMs: Long): CalculationWakeLock.Held {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
            // Not counted, so release() does nothing when the lock is already released, by its
            // timeout or by an earlier call. Held promises that release() is safe to call more than
            // once; a counted lock throws "WakeLock under-locked" on the second call.
            .apply { setReferenceCounted(false) }
        wakeLock.acquire(timeoutMs)
        return object : CalculationWakeLock.Held {
            override fun release() = wakeLock.release()
        }
    }

    companion object {

        const val TAG = "AAPS:Calculation"
    }
}
