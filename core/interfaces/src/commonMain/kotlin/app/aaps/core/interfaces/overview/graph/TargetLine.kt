package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.endOf
import app.aaps.core.data.model.latestRunningAt
import app.aaps.core.interfaces.profile.Profile

/** The middle of the profile's target range at [time], in mg/dl. The value the target line shows when no temporary target runs. */
fun Profile.targetMiddleMgdl(time: Long): Double = (getTargetLowMgdl(time) + getTargetHighMgdl(time)) / 2

/**
 * The target line of the BG graph, shared by the phone's graph and the watch's.
 *
 * A step line: the temporary target's middle while one runs, else the profile's target middle, from
 * [fromTime] to [endTime]. Only the points where the value changes are kept, plus a last point at
 * [endTime] with the last value, so a straight line between two points is the line. [endTime] may be
 * in the future: the line then shows how long a temporary target lasts.
 *
 * The value is read at [fromTime], at every whole five minutes of the clock (the profile's target
 * blocks change on those), and at the start and the end of every temporary target in the range. So
 * a step stands exactly where the temporary target starts or ends, and the phone and the watch, which
 * build the line over different ranges, get the same steps. A walk in five minute steps from
 * [fromTime] gave each side its own grid, and the steps sat up to five minutes apart.
 *
 * [temporaryTargets] are every temporary target that touches the range, both the one running at
 * [fromTime] and those starting later, oldest first. [profileTargetMgdl] gives the profile's target
 * middle at a time ([Profile.targetMiddleMgdl]); [toUnits] turns mg/dl into the units the line is
 * drawn in.
 */
fun buildTargetLine(
    fromTime: Long,
    endTime: Long,
    temporaryTargets: List<TT>,
    profileTargetMgdl: (Long) -> Double,
    toUnits: (Double) -> Double
): List<GraphDataPoint> {
    if (endTime <= fromTime) return emptyList()
    val times = mutableSetOf(fromTime)
    var gridTime = (fromTime / STEP_MS + 1) * STEP_MS
    while (gridTime < endTime) {
        times.add(gridTime)
        gridTime += STEP_MS
    }
    for (tt in temporaryTargets) {
        if (tt.timestamp > fromTime && tt.timestamp < endTime) times.add(tt.timestamp)
        val end = endOf(tt.timestamp, tt.duration)
        if (end > fromTime && end < endTime) times.add(end)
    }

    val targets = mutableListOf<GraphDataPoint>()
    var lastTarget = -1.0
    for (time in times.sorted()) {
        val tt = temporaryTargets.latestRunningAt(time) { it.duration }
        val mgdl = if (tt != null) (tt.lowTarget + tt.highTarget) / 2 else profileTargetMgdl(time)
        val value = toUnits(mgdl)
        if (value != lastTarget) {
            targets.add(GraphDataPoint(time, value))
            lastTarget = value
        }
    }
    if (lastTarget >= 0.0) targets.add(GraphDataPoint(endTime, lastTarget))
    return targets
}

private const val STEP_MS = 5 * 60 * 1000L
