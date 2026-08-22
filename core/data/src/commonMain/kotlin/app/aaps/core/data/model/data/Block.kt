package app.aaps.core.data.model.data

import kotlin.time.Duration.Companion.days

data class Block(val duration: Long, val amount: Double)

fun List<Block>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}