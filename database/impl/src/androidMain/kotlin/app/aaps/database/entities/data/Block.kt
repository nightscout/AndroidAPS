package app.aaps.database.entities.data

import kotlin.time.Duration.Companion.days

data class Block(var duration: Long, var amount: Double)

fun List<Block>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}