package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.pump.PumpProfile

/**
 * Profile with applied insulin configuration (Effective profile)
 */
interface EffectiveProfile : Profile {

    /** Applied insulin configuration */
    override val iCfg: ICfg

    /**
     * Convert EffectiveProfile to Concentrated using iCfg.concentration value
     *
     * if another concentration is put within the Pump (i.e. U200) iCfg.concentration should be set to 2.0
     * the EffectiveProfile (set in U100) should be converted to a "Concentrated Profile" to deliver the right rate in International Units
     *
     * @return PumpProfile
     **/

    fun toPump(): PumpProfile

    /**
     * True when [running] is the profile AAPS made from this one when it was set in the pump.
     *
     * Do not compare the values for this: the pump can round the basal, and the running profile then holds the
     * rates the pump really gives (#2454). The values would never match again, and AAPS would set the same
     * profile again and again. This default compares the values; a profile that knows its link overrides it.
     */
    fun isAppliedAs(running: Profile): Boolean = isEqual(running)

    /**
     * Active insulin concentration used
     * @return Insulin concentration (0.2 for U20, 2.0 for U200 insulin)
     * @See [app.aaps.core.interfaces.pump.PumpRate]
     * @See [app.aaps.core.interfaces.pump.PumpInsulin]
     */
    fun insulinConcentration(): Double = iCfg.concentration
}