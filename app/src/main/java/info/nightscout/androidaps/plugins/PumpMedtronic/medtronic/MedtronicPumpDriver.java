package info.nightscout.androidaps.plugins.PumpMedtronic.medtronic;

import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpDriver;

/**
 * Created by andy on 4/28/18.
 */

public class MedtronicPumpDriver extends VirtualPumpDriver /*implements PumpInterface*/ {


    public MedtronicPumpDriver()
    {
        // bolus
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d; // this needs to be reconfigurable

        // TBR
        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.ABSOLUTE;
        pumpDescription.maxTempAbsolute = 35.0d;
        //pumpDescription.maxTempPercent = 200;
        //pumpDescription.tempPercentStep = 1;
        pumpDescription.tempDurationStep = 30;
        pumpDescription.tempMaxDuration = 24 * 60;

        // extended bolus
        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.extendedBolusStep = 0.1d; // 0 - 25
        pumpDescription.extendedBolusDurationStep = 30;
        pumpDescription.extendedBolusMaxDuration = 8 * 60;

        // set basal profile
        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.05d;
        pumpDescription.basalMinimumRate = 0.05d;

        // ?
        pumpDescription.isRefillingCapable = false;

        // ?
        pumpDescription.storesCarbInfo = false;

        this.pumpStatusData = new MedtronicPumpStatus(pumpDescription);
    }
}
