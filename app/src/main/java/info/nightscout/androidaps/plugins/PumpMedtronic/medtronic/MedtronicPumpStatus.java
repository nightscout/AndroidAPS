package info.nightscout.androidaps.plugins.PumpMedtronic.medtronic;

import android.service.autofill.RegexValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.medtronic.defs.MedtronicPumpType;
import info.nightscout.utils.SP;

/**
 * Created by andy on 4/28/18.
 */

public class MedtronicPumpStatus extends PumpStatus {


    //private static MedtronicPumpStatus medtronicPumpStatus = new MedtronicPumpStatus();
    private static Logger LOG = LoggerFactory.getLogger(MedtronicPumpStatus.class);

    //public Date lastDataTime;
    //public long lastConnection = 0L;
    //public Date lastBolusTime;
    //public String activeProfile = "A";
    //public double reservoirRemainingUnits = 50d;
    //public double batteryRemaining = 75d;
    //public String iob = "0";

    public String errorDescription = null;
    public String serialNumber;
    public MedtronicPumpType pumpType = null;
    public String pumpFrequency = null;
    public String rileyLinkAddress = null;

    String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";
    String regexSN = "[0-9]{6}";



    public MedtronicPumpStatus(PumpDescription pumpDescription)
    {
        super(pumpDescription);
    }


    @Override
    public void initSettings() {
        this.activeProfile = "A";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75d;
    }


//
//    //public static MedtronicPumpStatus getInstance()
//    {
//        return medtronicPumpStatus;
//    }



    public void verifyConfiguration()
    {
        try {
            this.errorDescription = null;

            String serialNr = SP.getString("pref_medtronic_serial", null);

            if (serialNr == null) {
                this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_serial_not_set);
                return;
            } else {
                if (!serialNr.matches(regexSN)) {
                    this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_serial_invalid);
                    return;
                } else {
                    serialNumber = serialNr;
                }
            }

            String pumpType = SP.getString("pref_medtronic_pump_type", null);

            if (pumpType == null) {
                this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_pump_type_not_set);
                return;
            } else {
                String pumpTypePart = pumpType.substring(0, 3);

                if (!pumpTypePart.matches("[0-9]{3}")) {
                    this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_pump_type_invalid);
                    return;
                } else {
                    this.pumpType = MedtronicPumpType.getByCode(pumpTypePart);
                    setDescriptionFromPumpType();
                }
            }


            String pumpFrequency = SP.getString("pref_medtronic_frequency", null);

            if (pumpFrequency == null) {
                this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_pump_frequency_not_set);
                return;
            } else {
                if (!pumpFrequency.equals("US") && !pumpFrequency.equals("EU")) {
                    this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_pump_frequency_invalid);
                    return;
                } else {
                    this.pumpFrequency = pumpFrequency;
                }
            }


            String rileyLinkAddress = SP.getString("pref_medtronic_rileylink_mac", null);

            if (rileyLinkAddress == null) {
                this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_rileylink_address_invalid);
                return;
            } else {
                if (!rileyLinkAddress.matches(regexMac)) {
                    this.errorDescription = MainApp.sResources.getString(R.string.medtronic_error_rileylink_address_invalid);
                } else {
                    this.rileyLinkAddress = rileyLinkAddress;
                }
            }
        }
        catch(Exception ex)
        {
            this.errorDescription = ex.getMessage();
            LOG.error("Error on Verification: " + ex.getMessage(), ex);
        }
    }

    private void setDescriptionFromPumpType() {
        if (this.pumpType==MedtronicPumpType.Unknown)
            return;

        this.reservoirFullUnits = "" + this.pumpType.getReservoir();
    }


    public String getErrorInfo()
    {
        verifyConfiguration();

        return (this.errorDescription==null) ? "-" : this.errorDescription;
    }


}
