package info.nightscout.androidaps.plugins.PumpCommon.defs;

/**
 * Created by andy on 03/05/2018.
 */

public enum PumpCapability {

    Bolus, //
    ExtendedBolus, //
    TBR, //
    BasalProfileSet, //
    Refill, //
    StoreCarbInfo, //

    // grouped
    VirtualPump(Bolus, ExtendedBolus, TBR, BasalProfileSet, StoreCarbInfo), //

    Bolus_TBR_Basal_Refill_Carb(Bolus, TBR, BasalProfileSet, Refill, StoreCarbInfo), //
    Bolus_Extended_TBR_Basal_Carb(Bolus, ExtendedBolus, TBR, BasalProfileSet, StoreCarbInfo), //
    Bolus_Extended_TBR_Basal_Refill_Carb(Bolus, ExtendedBolus, TBR, BasalProfileSet, Refill, StoreCarbInfo), //

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
