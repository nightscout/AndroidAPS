package info.nightscout.androidaps.plugins.pump.common.defs;

/**
 * Created by andy on 03/05/2018.
 */

public enum PumpCapability {

    Bolus, // isBolusCapable
    ExtendedBolus, // isExtendedBolusCapable
    TempBasal, // isTempBasalCapable
    BasalProfileSet, // isSetBasalProfileCapable
    Refill, // isRefillingCapable
    ReplaceBattery, // isBatteryReplaceable
    StoreCarbInfo, // storesCarbInfo
    TDD, // supportsTDDs
    ManualTDDLoad, // needsManualTDDLoad
    BasalRate30min, // is30minBasalRatesCapable

    // grouped by pump
    VirtualPumpCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery), //
    ComboCapabilities(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad), //
    DanaCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad), //
    DanaWithHistoryCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, StoreCarbInfo, TDD, ManualTDDLoad), //
    InsightCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, BasalRate30min), //
    MedtronicCapabilities(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD), //
    OmnipodCapabilities(Bolus, TempBasal, BasalProfileSet, BasalRate30min), //
    YpsomedCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad),

    // BasalRates (separately grouped)
    BasalRate_Duration15minAllowed, //
    BasalRate_Duration30minAllowed, //
    BasalRate_Duration15and30minAllowed(BasalRate_Duration15minAllowed, BasalRate_Duration30minAllowed), //
    BasalRate_Duration15and30minNotAllowed, //
    ;

    PumpCapability[] children;


    PumpCapability() {
    }


    PumpCapability(PumpCapability... children) {
        this.children = children;
    }


    public boolean hasCapability(PumpCapability capability) {
        // we can only check presense of simple capabilities
        if (capability.children != null)
            return false;

        if (this == capability)
            return true;

        if (this.children != null) {
            for (PumpCapability child : children) {
                if (child == capability)
                    return true;
            }

            return false;
        } else
            return false;
    }


}
