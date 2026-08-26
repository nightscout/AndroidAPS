package app.aaps.implementation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.aaps.core.interfaces.protection.ProtectionCheck
import javax.inject.Inject

class ProcessLifecycleListener @Inject constructor(private val protectionCheck: ProtectionCheck) : DefaultLifecycleObserver {

    override fun onPause(owner: LifecycleOwner) {
        protectionCheck.resetAuthorization()
    }
}