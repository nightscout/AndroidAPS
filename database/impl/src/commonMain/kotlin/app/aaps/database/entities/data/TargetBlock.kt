package app.aaps.database.entities.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

// See Block: the property names are the JSON keys stored in the database.
@Serializable
data class TargetBlock(var duration: Long, var lowTarget: Double, var highTarget: Double)

fun List<TargetBlock>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}