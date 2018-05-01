package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class ChangeBolusWizardSetupPumpEvent extends TimeStampedRecord {

    public ChangeBolusWizardSetupPumpEvent() {

    }

    @Override
    public int getLength() {
        return 144;
    }

    @Override
    public String getShortTypeName() {
        return "Ch Bolus Wizard Setup";
    }
}
