package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.profile.EffectiveProfile

interface PumpWithConcentration : Pump {
    /*
     * Extend interface here if you need to miss concentration modification i.e. Filling bolus
     */

    /**
     * Return real pump (selected in ConfigBuilder)
     */
    fun selectedActivePump(): Pump

    /**
     *  Upload to pump new basal profile (and IC/ISF if supported by pump)
     *
     *  @param profile new profile
     */
    fun setNewBasalProfile(profile: EffectiveProfile): PumpEnactResult

    /**
     * @param profile profile to check
     *
     * @return true if pump is running the same profile as in param
     */
    fun isThisProfileSet(profile: EffectiveProfile): Boolean

}