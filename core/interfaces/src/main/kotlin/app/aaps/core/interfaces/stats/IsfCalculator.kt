package info.nightscout.interfaces.stats

import info.nightscout.interfaces.profile.Profile

interface IsfCalculator {
    fun calculate(profile : Profile, insulinDivisor: Int, glucose: Double, isTempTarget: Boolean) : IsfCalculation
}