package app.aaps.core.interfaces.pump.defs

class DoseSettings(val step: Double, val durationStep: Int, val maxDuration: Int, val minDose: Double, val maxDose: Double = Double.MAX_VALUE)