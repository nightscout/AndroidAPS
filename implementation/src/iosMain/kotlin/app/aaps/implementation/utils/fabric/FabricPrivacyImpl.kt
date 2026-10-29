package app.aaps.implementation.utils.fabric

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The iOS side of analytics and crash reporting: nothing leaves the phone, it is all written down.
 *
 * The Android implementation sends the same events to Firebase Analytics and Crashlytics. Firebase
 * does ship an Apple SDK, and reaching it is no longer the obstacle this comment once claimed it was
 * - the Xcode project already resolves Swift packages, and `FabricPrivacy` is already exported as an
 * Obj-C protocol, so a Swift class could implement it the way `NsSocketBridge.swift` implements
 * `NsSocketFactory`. What is missing is a decision, not a mechanism: two Apple apps registered in the
 * Firebase project, a privacy manifest and a privacy policy, and an answer on whether a medical app
 * should upload crash data by default at all.
 *
 * [fabricEnabled] answers **false**, like the desktop one, and that is the truthful answer rather
 * than a shrug: it reports whether anything is uploaded, and nothing is. It is what puts "Crash logs
 * upload disabled!" in the About dialog, which on iOS is simply true. This used to read the stored
 * preference, which meant the dialog told an iOS user reporting was on while nothing was sent.
 *
 * The methods below therefore log unconditionally and say "(not sent)", so the local record of what
 * happened is complete and nobody reads it as proof that something was reported. When Crashlytics is
 * wired up, [fabricEnabled] goes back to reading `BooleanKey.MaintenanceEnableFabric` and the gating
 * returns with it.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FabricPrivacyImpl(
    private val aapsLogger: AAPSLogger
) : FabricPrivacy {

    override fun fabricEnabled(): Boolean = false

    override fun setUserProperty(key: String, value: String) {
        aapsLogger.debug(LTag.CORE, "Analytics user property (not sent): $key = $value")
    }

    override fun logCustom(event: String) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $event")
    }

    override fun logCustom(name: String, params: Map<String, Long>) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $name $params")
    }

    override fun logMessage(message: String) {
        aapsLogger.info(LTag.CORE, "Analytics message (not sent): $message")
    }

    override fun logException(throwable: Throwable) {
        aapsLogger.error(LTag.CORE, "Exception (not reported to analytics): ", throwable)
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
