package info.nightscout.androidaps.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import info.nightscout.androidaps.logging.L
import org.slf4j.LoggerFactory

object ActivityMonitor : Application.ActivityLifecycleCallbacks {
    private val log = LoggerFactory.getLogger(L.CORE)
    override fun onActivityPaused(activity: Activity?) {
        log.debug("onActivityPaused " + activity?.localClassName)
    }

    override fun onActivityResumed(activity: Activity?) {
        log.debug("onActivityResumed " + activity?.localClassName)
    }

    override fun onActivityStarted(activity: Activity?) {
        log.debug("onActivityStarted " + activity?.localClassName)
    }

    override fun onActivityDestroyed(activity: Activity?) {
        log.debug("onActivityDestroyed " + activity?.localClassName)
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
        log.debug("onActivitySaveInstanceState " + activity?.localClassName)
    }

    override fun onActivityStopped(activity: Activity?) {
        log.debug("onActivityStopped " + activity?.localClassName)
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        log.debug("onActivityCreated " + activity?.localClassName)
    }
}