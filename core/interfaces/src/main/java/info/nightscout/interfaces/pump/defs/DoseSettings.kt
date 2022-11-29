package info.nightscout.interfaces.pump.defs

class DoseSettings constructor(val step: Double, val durationStep: Int, val maxDuration: Int, val minDose: Double, val maxDose: Double = Double.MAX_VALUE)