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
     * Active insulin concentration used
     * @return Insulin concentration (0.2 for U20, 2.0 for U200 insulin)
     * @See [app.aaps.core.interfaces.pump.PumpRate]
     * @See [app.aaps.core.interfaces.pump.PumpInsulin]
     */
    fun insulinConcentration(): Double = iCfg.concentration
}