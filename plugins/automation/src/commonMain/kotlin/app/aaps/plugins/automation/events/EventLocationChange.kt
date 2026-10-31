package app.aaps.plugins.automation.events

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.plugins.automation.GeoPosition

/**
 * The phone reported a new position, so any location rule is worth re-evaluating.
 *
 * **A notification, not a measurement.** The distance a `TriggerLocation` compares against is computed
 * by `LastKnownLocation.distanceTo`, which reads the platform's own last position - on Android that is
 * `LastLocationDataContainer.lastLocation`, still an `android.location.Location`, so the maths stays on
 * the WGS84 ellipsoid exactly as before. Nothing here is used to decide whether a rule fires; the
 * payload only reaches a debug log.
 *
 * That is why it can carry [GeoPosition] instead of `android.location.Location`: no trigger result
 * depends on it.
 *
 * @param provider which source reported it ("gps", "network", ...) - kept because it is the quickest
 *   way to tell a precise fix from a coarse one when reading a log
 */
data class EventLocationChange(val position: GeoPosition, val provider: String?) : Event()
