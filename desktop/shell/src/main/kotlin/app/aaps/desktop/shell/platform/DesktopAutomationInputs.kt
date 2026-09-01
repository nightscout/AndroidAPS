package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.plugins.automation.LastKnownLocation
import app.aaps.plugins.automation.LocationPermissions
import app.aaps.plugins.automation.PairedBtDevices
import app.aaps.plugins.automation.GeoPosition
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The automation inputs a desktop cannot answer.
 *
 * These four feed automation triggers - "when I connect to this car's Bluetooth", "when I am near
 * home". A desktop has no paired pump-side Bluetooth list and no location service, so it cannot
 * answer them, and this file is about answering **"I do not know"** rather than "no".
 *
 * That distinction is the whole point. `PairedBtDevices.names()` and `LastKnownLocation.position()`
 * both return a nullable, and null is the designed way to say the platform cannot tell. An empty
 * list would mean "you have no paired devices", which is a different claim and a wrong one - the
 * kind of quiet answer that makes a trigger the user relies on simply never fire, with nothing in
 * the log to explain it. This tree has already had that exact bug once, when a lost caller left the
 * Bluetooth device picker permanently empty.
 *
 * Every call is logged, so a rule that reaches for one of these says so.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopPairedBtDevices @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PairedBtDevices {

    /** Null, not an empty list: "cannot tell" rather than "none paired". */
    override fun names(): List<String>? {
        aapsLogger.debug(LTag.AUTOMATION, "Desktop has no paired Bluetooth device list")
        return null
    }
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopLastKnownLocation @Inject constructor(
    private val aapsLogger: AAPSLogger
) : LastKnownLocation {

    /** Null for the same reason: unknown, not "at the origin". */
    override fun position(): GeoPosition? {
        aapsLogger.debug(LTag.AUTOMATION, "Desktop has no location service")
        return null
    }

    /**
     * Null rather than a number.
     *
     * A distance of zero would read as "you are exactly there" and fire a location trigger for a
     * machine that has no idea where it is.
     */
    override fun distanceTo(latitude: Double, longitude: Double): Double? {
        aapsLogger.debug(LTag.AUTOMATION, "Desktop cannot measure distance without a location")
        return null
    }
}

/**
 * Nothing to ask the user for.
 *
 * Empty is the honest answer here, unlike the two above: a desktop JVM has no runtime permission
 * model, so there genuinely are no permission groups to grant. The permission screen then shows
 * nothing rather than asking for something that cannot be given.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopLocationPermissions @Inject constructor() : LocationPermissions {

    override fun groups(): List<PermissionGroup> = emptyList()
}

/**
 * There is no location service to switch on.
 *
 * Logged at error level when something asks for updates, because that means a rule is expecting
 * location and will never get it. Switching them off is not a problem, so that stays quiet.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopLocationServiceController @Inject constructor(
    private val aapsLogger: AAPSLogger
) : LocationServiceController {

    override fun setLocationUpdatesEnabled(enabled: Boolean) {
        if (enabled) aapsLogger.error(LTag.AUTOMATION, "Location updates were asked for, but desktop has no location service")
    }
}
