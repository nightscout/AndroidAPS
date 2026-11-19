package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The partial wake lock [RandomBgPlugin] has always held, from its start to its stop. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidRandomBgWakeLock(private val context: Context) : RandomBgWakeLock {

    // No timeout, as before: the plugin holds it for as long as it runs and releases it when it stops.
    @SuppressLint("WakelockTimeout")
    override fun acquire(): RandomBgWakeLock.Held? {
        val lock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAPS:RandomBgPlugin")
            ?: return null
        lock.acquire()
        return object : RandomBgWakeLock.Held {
            override fun release() {
                if (lock.isHeld) lock.release()
            }
        }
    }
}
