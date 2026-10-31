package app.aaps.implementation.utils.fabric

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The iOS side of analytics and crash reporting, which for now only writes to the log.
 *
 * The Android implementation sends the same events to Firebase Analytics and Crashlytics. Firebase
 * does ship an Apple SDK, so this is not a platform limitation - but reaching it from Kotlin means
 * cinterop against a CocoaPods or SwiftPM dependency, and this project has neither. Adding a
 * dependency manager to the build is a decision worth making on its own rather than as a side
 * effect of a port.
 *
 * Until then the class is deliberately whole rather than empty. Every method the Android one has
 * writes the same log line it writes there before forwarding to Firebase, so the local record of
 * what happened is identical on both platforms. What is missing is only the upload.
 *
 * [fabricEnabled] reads the same preference as Android, so a user who has turned reporting off has
 * it off here too. That matters even with nothing being uploaded: the flag also guards what gets
 * written down.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FabricPrivacyImpl(
    private val aapsLogger: AAPSLogger,
    private val store: KeyValueStore
) : FabricPrivacy {

    override fun fabricEnabled(): Boolean =
        store.getBoolean(BooleanKey.MaintenanceEnableFabric.key, BooleanKey.MaintenanceEnableFabric.defaultValue)

    override fun setUserProperty(key: String, value: String) {
        if (fabricEnabled()) aapsLogger.debug(LTag.CORE, "Analytics user property: $key = $value")
    }

    override fun logCustom(event: String) {
        if (fabricEnabled()) aapsLogger.debug(LTag.CORE, "Analytics event: $event")
    }

    override fun logCustom(name: String, params: Map<String, Long>) {
        if (fabricEnabled()) aapsLogger.debug(LTag.CORE, "Analytics event: $name $params")
    }

    override fun logMessage(message: String) {
        aapsLogger.info(LTag.CORE, "Crashlytics log message: $message")
    }

    override fun logException(throwable: Throwable) {
        aapsLogger.error(LTag.CORE, "Crashlytics log exception: ", throwable)
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
}
