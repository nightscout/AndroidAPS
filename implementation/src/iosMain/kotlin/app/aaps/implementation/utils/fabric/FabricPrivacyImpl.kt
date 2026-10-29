package app.aaps.implementation.utils.fabric

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.fabric.CrashReporter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The iOS side of analytics and crash reporting.
 *
 * Crashes go to Firebase Crashlytics through [CrashReporter], which is implemented in Swift. There
 * is no Analytics here and that is deliberate: `FirebaseCrashlytics` for Apple builds from source
 * through SwiftPM, while `FirebaseAnalytics` arrives as a closed prebuilt binary that pulls in
 * GoogleAppMeasurement and an advertising identifier surface. A medical app does not need it, so the
 * analytics calls below are written down locally and go no further.
 *
 * ## The opt-out
 *
 * [fabricEnabled] reads the same stored key as Android, but **defaults to false** where Android
 * defaults to true. Reporting from iPhones is new, so it starts off and the user turns it on, rather
 * than starting on under a switch that Simple Mode hides. A value the user has actually set is
 * honoured either way, including one that arrives through a settings import from an Android phone.
 *
 * The flag is re-applied to Crashlytics on every start rather than trusted to stick, because
 * Crashlytics keeps its own copy in its own `UserDefaults`, outside the AAPS store - so a "start
 * fresh" or an import could otherwise leave the two disagreeing.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FabricPrivacyImpl(
    private val aapsLogger: AAPSLogger,
    private val store: KeyValueStore,
    private val crashReporter: CrashReporter
) : FabricPrivacy {

    override fun fabricEnabled(): Boolean =
        store.getBoolean(BooleanKey.MaintenanceEnableFabric.key, OFF_UNTIL_ASKED)

    override fun setUserProperty(key: String, value: String) {
        aapsLogger.debug(LTag.CORE, "Analytics user property (not sent): $key = $value")
        if (fabricEnabled()) crashReporter.setCustomKey(key, value)
    }

    override fun logCustom(event: String) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $event")
    }

    override fun logCustom(name: String, params: Map<String, Long>) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $name $params")
    }

    override fun logMessage(message: String) {
        aapsLogger.info(LTag.CORE, "Crashlytics log message: $message")
        if (fabricEnabled()) crashReporter.log(message)
    }

    override fun logException(throwable: Throwable) {
        aapsLogger.error(LTag.CORE, "Crashlytics log exception: ", throwable)
        if (fabricEnabled()) crashReporter.recordException(
            type = throwable::class.simpleName ?: "Throwable",
            message = throwable.message ?: "",
            stackTrace = throwable.stackTraceToString().lines()
        )
    }

    /**
     * Recorded rather than dropped, though no watch can reach an iPhone build today.
     *
     * The Android version deserialises a Java `Throwable` out of the bytes the watch sent, which
     * Kotlin/Native cannot do. The fields around it are the useful part anyway: they say which
     * watch it was.
     */
    override fun logWearException(wearException: EventData.WearException) {
        aapsLogger.debug(
            LTag.WEAR,
            "logWearException board=${wearException.board} model=${wearException.model} " +
                "manufacturer=${wearException.manufacturer} product=${wearException.product} " +
                "sdk=${wearException.sdk} fingerprint=${wearException.fingerprint}"
        )
    }

    private companion object {

        /**
         * The iOS default for `MaintenanceEnableFabric`, deliberately not the shared one.
         *
         * `BooleanKey.MaintenanceEnableFabric.defaultValue` is true, which is the state of the
         * Android fleet and not a decision this platform has to repeat. Changing the shared default
         * would turn reporting off for every existing Android user, so the difference lives here.
         */
        private const val OFF_UNTIL_ASKED = false
    }
}
