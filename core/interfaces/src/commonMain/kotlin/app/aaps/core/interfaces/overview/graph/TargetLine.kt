package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.latestRunningAt
import app.aaps.core.interfaces.profile.Profile

/** The middle of the profile's target range at [time], in mg/dl. The value the target line shows when no temporary target runs. */
fun Profile.targetMiddleMgdl(time: Long): Double = (getTargetLowMgdl(time) + getTargetHighMgdl(time)) / 2

/**
 * The target line of the BG graph, shared by the phone's graph and the watch's.
 *
 * A step line: the temporary target's middle while one runs, else the profile's target middle, walked
 * from [fromTime] to [endTime] in 5 minute steps. Only the points where the value changes are kept,
 * plus a last point at [endTime] with the last value, so a straight line between two points is the
 * line. [endTime] may be in the future: the line then shows how long a temporary target lasts.
 *
 * [temporaryTargets] are every temporary target that touches the range, both the one running at
 * [fromTime] and those starting later. [profileTargetMgdl] gives the profile's target middle at a
 * time ([Profile.targetMiddleMgdl]); [toUnits] turns mg/dl into the units the line is drawn in.
 */
fun buildTargetLine(
    fromTime: Long,
    endTime: Long,
    temporaryTargets: List<TT>,
    profileTargetMgdl: (Long) -> Double,
    toUnits: (Double) -> Double
): List<GraphDataPoint> {
    val targets = mutableListOf<GraphDataPoint>()
    var lastTarget = -1.0
    var time = fromTime
    while (time < endTime) {
        val tt = temporaryTargets.latestRunningAt(time) { it.duration }
        val mgdl = if (tt != null) (tt.lowTarget + tt.highTarget) / 2 else profileTargetMgdl(time)
        val value = toUnits(mgdl)
        if (value != lastTarget) {
            targets.add(GraphDataPoint(time, value))
            lastTarget = value
        }
        time += STEP_MS
    }
    if (lastTarget >= 0.0) targets.add(GraphDataPoint(endTime, lastTarget))
    return targets
}

private const val STEP_MS = 5 * 60 * 1000L
