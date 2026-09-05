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
 * home". A desktop has neither a paired Bluetooth list nor a location service, so what this file is
 * really about is which kind of "no" each interface asks for. They are not the same, and reading
 * them as one is what put a false error on the trigger editor.
 *
 * `LastKnownLocation.position()` returns null for "I do not know", and that is right here: a
 * position of zero would read as "you are exactly there" and fire a location trigger for a machine
 * with no idea where it is.
 *
 * `PairedBtDevices.names()` looks the same and is not. Its null means specifically **"not allowed,
 * and the user can grant it"** - the trigger editor turns null into a red "grant the Connect
 * permission" snackbar. A desktop has no such permission, so answering null asked the user to fix
 * something that does not exist, on every visit to the editor, which is how a screen teaches people
 * to ignore its errors. An empty list is the honest answer: there is no list, and no permission
 * would produce one. iOS reached the same conclusion for the same reason - see `IosPairedBtDevices`.
 *
 * Every call is logged, so a rule that reaches for one of these says so.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopPairedBtDevices @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PairedBtDevices {

    /** Empty, not null: "there is no such list here", not "ask the user for a permission". */
    override fun names(): List<String> {
        aapsLogger.debug(LTag.AUTOMATION, "Desktop has no paired Bluetooth device list, returning none")
        return emptyList()
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
