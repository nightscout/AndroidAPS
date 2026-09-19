package app.aaps.database.entities.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

// Serializable so the Room type converter can write this without org.json, which is Android only.
// The property names are the JSON keys stored in the database - see ProfileBlocksFormatTest.
@Serializable
data class Block(var duration: Long, var amount: Double)

fun List<Block>.checkSanity(): Boolean {
    var sum = 0L
    forEach { sum += it.duration }
    return sum == 1.days.inWholeMilliseconds
}