package app.aaps.core.data.model

import app.aaps.core.data.time.T
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How long a temporary basal or extended bolus has been running, and how long it was meant to run.
 *
 * These live next to the models rather than in `:core:objects` because they read nothing but the
 * record's own timestamp and duration. That matters beyond tidiness: `:core:objects` depends on
 * `:core:ui`, so anything that stayed there could not be used from `:core:ui` without a dependency
 * cycle - which is exactly what kept the `toStringFull` family from moving to the layer that draws.
 */

/** Minutes elapsed between the start and [time], never counting past the end. */
fun TB.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

/** Minutes elapsed between the start and [time], never counting past the end. */
fun EB.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

/** The full planned length, in minutes. */
val TB.durationInMinutes: Long
    get() = T.msecs(duration).mins()
