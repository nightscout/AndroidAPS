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
    StoreCarbInfo, // storesCarbInfo
    TDD, // supportsTDDs
    ManualTDDLoad, // needsManualTDDLoad
    BasalRate30min, // is30minBasalRatesCapable

    // grouped by pump
    VirtualPumpCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill), //
    ComboCapabilities(Bolus, TempBasal, BasalProfileSet, Refill, TDD, ManualTDDLoad), //
    DanaCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, TDD, ManualTDDLoad), //
    DanaWithHistoryCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, StoreCarbInfo, TDD, ManualTDDLoad), //
    InsightCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill,TDD,BasalRate30min), //


    // BasalRates (separately grouped)
    BasalRate_Duration15minAllowed, //
    BasalRate_Duration30minAllowed, //
    BasalRate_Duration15and30minAllowed(BasalRate_Duration15minAllowed, BasalRate_Duration30minAllowed), //
    BasalRate_Duration15and30minNotAllowed, //
    ;

    PumpCapability[] children;


    PumpCapability()
    {
    }


    PumpCapability(PumpCapability...children)
    {
        this.children = children;
    }


    public boolean hasCapability(PumpCapability capability)
    {
        // we can only check presense of simple capabilities
        if (capability.children != null)
            return false;

        if (this == capability)
            return true;

        if (this.children!=null)
        {
            for (PumpCapability child : children) {
                if (child == capability)
                    return true;
            }

            return false;
        }
        else
            return false;
    }



}
