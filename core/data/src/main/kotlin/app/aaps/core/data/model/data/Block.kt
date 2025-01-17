package app.aaps.core.data.model.data

import java.util.concurrent.TimeUnit

data class Block(var duration: Long, var amount: Double)

fun List<Block>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == TimeUnit.DAYS.toMillis(1)
}