package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.utils.T
import kotlin.math.roundToInt

fun Carbs.expandCarbs(): List<Carbs> =
    mutableListOf<Carbs>().also { carbs ->
        if (this.duration == 0L) {
            carbs.add(this)
        } else {
            var remainingCarbs = this.amount
            val ticks = T.msecs(this.duration).mins() * 4 //duration guaranteed to be integer greater zero
            for (i in 0 until ticks) {
                val carbTime = this.timestamp + i * 15 * 60 * 1000
                val smallCarbAmount = (1.0 * remainingCarbs / (ticks - i)).roundToInt() //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
                remainingCarbs -= smallCarbAmount.toLong()
                if (smallCarbAmount > 0)
                    carbs.add(Carbs(
                        timestamp = carbTime,
                        amount = smallCarbAmount.toDouble(),
                        duration = 0
                    ))
            }
        }
    }
