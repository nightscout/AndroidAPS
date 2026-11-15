package app.aaps.core.data.model.data

import kotlin.time.Duration.Companion.days

data class TargetBlock(val duration: Long, val lowTarget: Double, val highTarget: Double)

fun List<TargetBlock>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}