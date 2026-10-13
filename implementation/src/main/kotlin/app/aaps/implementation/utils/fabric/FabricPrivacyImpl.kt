package app.aaps.implementation.utils.fabric

import android.content.SharedPreferences
import android.os.Bundle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.Firebase
import dagger.Reusable
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.ObjectInputStream
import javax.inject.Inject

/**
 * Some users do not wish to be tracked, Fabric Answers and Crashlytics do not provide an easy way
 * to disable them and make calls from a potentially invalid singleton reference. This wrapper
 * emulates the methods but ignores the request if the instance is null or invalid.
 */
@Reusable
class FabricPrivacyImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sharedPreferences: SharedPreferences // Injecting Preferences is causing circular dependencies
) : FabricPrivacy {

    private val firebaseAnalytics: FirebaseAnalytics = Firebase.analytics

    init {
        firebaseAnalytics.setAnalyticsCollectionEnabled(!java.lang.Boolean.getBoolean("disableFirebase") && fabricEnabled())
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !java.lang.Boolean.getBoolean("disableFirebase") && fabricEnabled()
    }

    override fun setUserProperty(key: String, value: String) {
        firebaseAnalytics.setUserProperty(key, value)
    }

    // Analytics logCustom
    @Suppress("unused")
    override fun logCustom(name: String, event: Bundle) {
        try {
            if (fabricEnabled()) {
                firebaseAnalytics.logEvent(name, event)
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
    @Suppress("unused")
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
    override fun logCustom(event: String) {
        try {
            if (fabricEnabled()) {
                firebaseAnalytics.logEvent(event, Bundle())
            } else {
                aapsLogger.debug(LTag.CORE, "Ignoring recently opted-out event: $event")
            }
        } catch (_: NullPointerException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        } catch (_: IllegalStateException) {
            aapsLogger.debug(LTag.CORE, "Ignoring opted-out non-initialized event: $event")
        }
    }

    // Crashlytics log message
    override fun logMessage(message: String) {
        aapsLogger.info(LTag.CORE, "Crashlytics log message: $message")
        FirebaseCrashlytics.getInstance().log(message)
    }

    // Crashlytics logException
    override fun logException(throwable: Throwable) {
        aapsLogger.error("Crashlytics log exception: ", throwable)
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    override fun fabricEnabled(): Boolean {
        return sharedPreferences.getBoolean(BooleanKey.MaintenanceEnableFabric.key, true)
    }

    override fun logWearException(wearException: EventData.WearException) {
        aapsLogger.debug(LTag.WEAR, "logWearException")
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("wear_exception", true)
            setCustomKey("wear_board", wearException.board)
            setCustomKey("wear_fingerprint", wearException.fingerprint)
            setCustomKey("wear_sdk", wearException.sdk)
            setCustomKey("wear_model", wearException.model)
            setCustomKey("wear_manufacturer", wearException.manufacturer)
            setCustomKey("wear_product", wearException.product)
        }
        logException(byteArrayToThrowable(wearException.exception))
    }

    private fun byteArrayToThrowable(wearExceptionData: ByteArray): Throwable {
        val bis = ByteArrayInputStream(wearExceptionData)
        try {
            val ois = ObjectInputStream(bis)
            return ois.readObject() as Throwable
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        return IllegalArgumentException("Wear Exception could not be de-serialized")
    }
}
