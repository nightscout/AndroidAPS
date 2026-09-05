package app.aaps.plugins.aps.openAPSAutoISF.extensions

import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round

fun GlucoseStatusAutoIsf.log(decimalFormatter: DecimalFormatter): String = "Glucose: " + decimalFormatter.to0Decimal(glucose) + " mg/dl " +
    "Noise: " + decimalFormatter.to0Decimal(noise) + " " +
    "Delta: " + decimalFormatter.to0Decimal(delta) + " mg/dl" +
    "Short avg. delta: " + " " + decimalFormatter.to2Decimal(shortAvgDelta) + " mg/dl " +
    "Long avg. delta: " + decimalFormatter.to2Decimal(longAvgDelta) + " mg/dl " +
    "Dura ISF minutes: " + decimalFormatter.to2Decimal(duraISFminutes) + " m " +
    "Dura ISF average: " + decimalFormatter.to2Decimal(duraISFaverage) + " mg/dl " +
    "Parabola minutes: " + decimalFormatter.to2Decimal(parabolaMinutes) + " m " +
    "Parabola correlation: " + decimalFormatter.to2Decimal(corrSqu) + " " +
    "Parabola fit a0: " + decimalFormatter.to2Decimal(a0) + " mg/dl " +
    "Parabola fit a1: " + decimalFormatter.to2Decimal(a1) + " mg/dl/5m " +
    "Parabola fit a2: " + decimalFormatter.to2Decimal(a2) + " mg/dl/(5m)^2"

fun GlucoseStatusAutoIsf.asRounded() = copy(
    glucose = Round.roundTo(glucose, 0.1),
    noise = Round.roundTo(noise, 0.01),
    delta = Round.roundTo(delta, 0.01),
    shortAvgDelta = Round.roundTo(shortAvgDelta, 0.01),
    longAvgDelta = Round.roundTo(longAvgDelta, 0.01),
    duraISFminutes = Round.roundTo(duraISFminutes, 0.1),
    duraISFaverage = Round.roundTo(duraISFaverage, 0.1),
    parabolaMinutes = Round.roundTo(parabolaMinutes, 0.1),
    corrSqu = Round.roundTo(corrSqu, 0.0001),
    a0 = Round.roundTo(a0, 0.1),
    a1 = Round.roundTo(a1, 0.01),
    a2 = Round.roundTo(a2, 0.01)
)