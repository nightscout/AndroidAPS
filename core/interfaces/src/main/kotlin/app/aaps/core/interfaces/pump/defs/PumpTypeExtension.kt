package app.aaps.core.interfaces.pump.defs

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.utils.Round
import kotlin.math.min

fun PumpType.baseBasalRange(): String =
    if (baseBasalMaxValue() == null) baseBasalMinValue().toString()
    else "${baseBasalMinValue()}-${baseBasalMaxValue()}"

fun PumpType.hasExtendedBasals(): Boolean = baseBasalSpecialSteps() != null || specialBolusSize() != null

fun PumpType.determineCorrectBolusSize(bolusAmount: Double): Double =
    Round.roundTo(bolusAmount, specialBolusSize()?.getStepSizeForAmount(bolusAmount) ?: bolusSize())

fun PumpType.determineCorrectBolusStepSize(bolusAmount: Double): Double =
    specialBolusSize()?.getStepSizeForAmount(bolusAmount) ?: bolusSize()

fun PumpType.determineCorrectExtendedBolusSize(bolusAmount: Double): Double {
    val ebSettings = extendedBolusSettings() ?: throw IllegalStateException()
    return Round.roundTo(min(bolusAmount, ebSettings.maxDose), ebSettings.step)
}

fun PumpType.determineCorrectBasalSize(basalAmount: Double): Double {
    val tSettings = tbrSettings() ?: throw IllegalStateException()
    return Round.roundTo(min(basalAmount, tSettings.maxDose), baseBasalSpecialSteps()?.getStepSizeForAmount(basalAmount) ?: baseBasalStep())
}