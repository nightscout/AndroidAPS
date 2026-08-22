package app.aaps.core.data.pump.defs

enum class PumpCapability {

    // grouped by pump
    MDI(arrayOf(Capability.Bolus)),
    VirtualPumpCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery)),
    ComboCapabilities(arrayOf(Capability.Bolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD, Capability.ManualTDDLoad)),
    DanaCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD, Capability.ManualTDDLoad)),

    DanaWithHistoryCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD, Capability.ManualTDDLoad)),
    InsightCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.BasalRate30min)),
    MedtronicCapabilities(arrayOf(Capability.Bolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD)),
    OmnipodCapabilities(arrayOf(Capability.Bolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.BasalRate30min)),
    YpsomedCapabilities(
        arrayOf(
            Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD, Capability.ManualTDDLoad
        )
    ),  // BasalRates (separately grouped)
    DiaconnCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.Refill, Capability.ReplaceBattery, Capability.TDD, Capability.ManualTDDLoad)), //
    EopatchCapabilities(arrayOf(Capability.Bolus, Capability.ExtendedBolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.BasalRate30min)),
    MedtrumCapabilities(arrayOf(Capability.Bolus, Capability.TempBasal, Capability.BasalProfileSet, Capability.BasalRate30min, Capability.TDD)), // Technically the pump supports ExtendedBolus, but not implemented (yet)
    ;

    var children: ArrayList<Capability> = ArrayList()

    constructor(list: Array<Capability>) {
        children.addAll(list)
    }

    fun hasCapability(capability: Capability): Boolean = children.contains(capability)
}