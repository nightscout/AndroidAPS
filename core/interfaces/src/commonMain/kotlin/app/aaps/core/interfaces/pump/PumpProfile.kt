package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.profile.Profile

/**
 * This class represents concentrated Profile synchronised within the pump.
 *
 * Example: when using U20 insulin within the Pump,
 * if EffectiveProfile define a basal rate of 0.6U/h, pump should deliver 0.6 * (100 / 20) = 3.0U/h
 * In this case pump must use a rate of 3.0U/hour
 */
interface PumpProfile : Profile