package app.aaps.core.data.model.data

import java.util.concurrent.TimeUnit

data class TargetBlock(var duration: Long, var lowTarget: Double, var highTarget: Double)

fun List<TargetBlock>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == TimeUnit.DAYS.toMillis(1)
}