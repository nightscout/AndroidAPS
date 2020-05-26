package info.nightscout.androidaps.utils

import android.content.Context
import android.os.Bundle
import com.crashlytics.android.Crashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.fabric.sdk.android.Fabric
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Some users do not wish to be tracked, Fabric Answers and Crashlytics do not provide an easy way
 * to disable them and make calls from a potentially invalid singleton reference. This wrapper
 * emulates the methods but ignores the request if the instance is null or invalid.
 */
@Singleton
class FabricPrivacy @Inject constructor(
    context: Context,
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) {

    var firebaseAnalytics: FirebaseAnalytics

    init {
        instance = this
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        firebaseAnalytics.setAnalyticsCollectionEnabled(!java.lang.Boolean.getBoolean("disableFirebase") && fabricEnabled())

        if (fabricEnabled()) {
            Fabric.with(context, Crashlytics())
        }
    }

    companion object {
        private lateinit var instance: FabricPrivacy

        @JvmStatic
        @Deprecated("Use Dagger")
        fun getInstance(): FabricPrivacy = instance
    }

    // Analytics logCustom
    fun logCustom(event: Bundle) {
        try {
            if (fabricEnabled()) {
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_ITEM, event)
            } else {
                aapsLogger.debug(LTag.CORE, "Ignoring recently opted-out event: $event")
            }
        } catch (e: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        }
    }

    // Analytics logCustom
    fun logCustom(event: String) {
        try {
            if (fabricEnabled()) {
                firebaseAnalytics.logEvent(event, Bundle())
            } else {
                aapsLogger.debug(LTag.CORE, "Ignoring recently opted-out event: $event")
            }
        } catch (e: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        }
    }

    // Crashlytics logException
    fun logException(throwable: Throwable) {
        try {
            val crashlytics = Crashlytics.getInstance()
            crashlytics.core.logException(throwable)
            aapsLogger.debug(LTag.CORE, "Exception: ", throwable)
        } catch (e: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $throwable")
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $throwable")
        }
    }

    // Crashlytics log
    fun log(msg: String) {
        try {
            val crashlytics = Crashlytics.getInstance()
            crashlytics.core.log(msg)
        } catch (e: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $msg")
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $msg")
        }
    }

    // Crashlytics log
    fun log(priority: Int, tag: String?, msg: String) {
        try {
            val crashlytics = Crashlytics.getInstance()
            crashlytics.core.log(priority, tag, msg)
        } catch (e: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $msg")
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted out non-initialized log: $msg")
        }
    }

    fun fabricEnabled(): Boolean {
        return sp.getBoolean(R.string.key_enable_fabric, true)
    }
}