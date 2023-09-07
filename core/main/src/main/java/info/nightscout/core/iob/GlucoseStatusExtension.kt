package info.nightscout.core.iob

import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.Round

fun GlucoseStatus.log(decimalFormatter: DecimalFormatter): String = "Glucose: " + decimalFormatter.to0Decimal(glucose) + " mg/dl " +
    "Noise: " + decimalFormatter.to0Decimal(noise) + " " +
    "Delta: " + decimalFormatter.to0Decimal(delta) + " mg/dl" +
    "Short avg. delta: " + " " + decimalFormatter.to2Decimal(shortAvgDelta) + " mg/dl " +
    "Long avg. delta: " + decimalFormatter.to2Decimal(longAvgDelta) + " mg/dl"

fun GlucoseStatus.asRounded() = copy(
    glucose = Round.roundTo(glucose, 0.1),
    noise = Round.roundTo(noise, 0.01),
    delta = Round.roundTo(delta, 0.01),
    shortAvgDelta = Round.roundTo(shortAvgDelta, 0.01),
    longAvgDelta = Round.roundTo(longAvgDelta, 0.01)
)