package app.aaps.core.data.pump.defs

enum class Capability {
    Bolus,  // isBolusCapable
    ExtendedBolus,  // isExtendedBolusCapable
    TempBasal,  // isTempBasalCapable
    BasalProfileSet,  // isSetBasalProfileCapable
    Refill,  // isRefillingCapable
    ReplaceBattery,  // isBatteryReplaceable

    // StoreCarbInfo,  // removed. incompatible with storing notes with carbs
    TDD,  // supportsTDDs
    ManualTDDLoad,  // needsManualTDDLoad
    BasalRate30min,  // is30minBasalRatesCapable
    BasalRate_Duration15minAllowed,
    BasalRate_Duration30minAllowed;
}