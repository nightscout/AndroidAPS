package app.aaps.database.entities.data

import kotlin.time.Duration.Companion.days

data class TargetBlock(var duration: Long, var lowTarget: Double, var highTarget: Double)

fun List<TargetBlock>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}