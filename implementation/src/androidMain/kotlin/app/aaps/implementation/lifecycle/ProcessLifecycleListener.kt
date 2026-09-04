package app.aaps.implementation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.aaps.core.interfaces.protection.ProtectionCheck
import dev.zacsweers.metro.Inject

/**
 * Drops a granted PIN or biometric session when the app leaves the screen.
 *
 * One of three, and they have to stay in step: `IosProtectionLifecycle` does this from
 * `didEnterBackground`, and the desktop shell from the window losing focus. Only this one existed
 * for a while, so on the other two platforms an authorization survived leaving the app.
 */
class ProcessLifecycleListener @Inject constructor(private val protectionCheck: ProtectionCheck) : DefaultLifecycleObserver {

    override fun onPause(owner: LifecycleOwner) {
        protectionCheck.resetAuthorization()
    }
}