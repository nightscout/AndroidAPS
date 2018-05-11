package info.nightscout.androidaps.plugins.PumpMedtronic.medtronic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
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
    //public String activeProfileName = "A";
    //public double reservoirRemainingUnits = 50d;
    //public double batteryRemaining = 75d;
    //public String iob = "0";

    public String errorDescription = null;
    public String serialNumber;
    public PumpType pumpType = null;
    public String pumpFrequency = null;
    public String rileyLinkAddress = null;
    public Integer maxBolus;
    public Integer maxBasal;
    private String[] frequencies;
    private boolean isFrequencyUS = false;

    String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";
    String regexSN = "[0-9]{6}";

    private Map<String,PumpType> medtronicPumpMap = null;


    public MedtronicPumpStatus(PumpDescription pumpDescription)
    {
        super(pumpDescription);
    }


    @Override
    public void initSettings() {

        this.activeProfileName = "STD";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75d;

        if (this.medtronicPumpMap==null)
            createMedtronicPumpMap();
    }

    private void createMedtronicPumpMap() {

        medtronicPumpMap = new HashMap<>();
        medtronicPumpMap.put("512", PumpType.Minimed_512_712);
        medtronicPumpMap.put("712", PumpType.Minimed_512_712);
        medtronicPumpMap.put("515", PumpType.Minimed_515_715);
        medtronicPumpMap.put("715", PumpType.Minimed_515_715);

        medtronicPumpMap.put("522", PumpType.Minimed_522_722);
        medtronicPumpMap.put("722", PumpType.Minimed_522_722);
        medtronicPumpMap.put("523", PumpType.Minimed_523_723);
        medtronicPumpMap.put("723", PumpType.Minimed_523_723);
        medtronicPumpMap.put("554", PumpType.Minimed_554_754_Veo);
        medtronicPumpMap.put("754", PumpType.Minimed_554_754_Veo);

        frequencies = new String[2];
        frequencies[0] = MainApp.gs(R.string.medtronic_pump_frequency_us);
        frequencies[1] = MainApp.gs(R.string.medtronic_pump_frequency_worldwide);
    }


    public void verifyConfiguration()
    {
        try {

            if (this.medtronicPumpMap==null)
                createMedtronicPumpMap();


            this.errorDescription = null;
            this.serialNumber = null;
            this.pumpType = null;
            this.pumpFrequency = null;
            this.rileyLinkAddress = null;
            this.maxBolus = null;
            this.maxBasal = null;


            String serialNr = SP.getString("pref_medtronic_serial", null);

            if (serialNr == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_serial_not_set);
                return;
            } else {
                if (!serialNr.matches(regexSN)) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_serial_invalid);
                    return;
                } else {
                    this.serialNumber = serialNr;
                }
            }


            String pumpType = SP.getString("pref_medtronic_pump_type", null);

            if (pumpType == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_type_not_set);
                return;
            } else {
                String pumpTypePart = pumpType.substring(0, 3);

                if (!pumpTypePart.matches("[0-9]{3}")) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_type_invalid);
                    return;
                } else {
                    this.pumpType = medtronicPumpMap.get(pumpTypePart);

                    if (pumpTypePart.startsWith("7"))
                        this.reservoirFullUnits = "300";
                    else
                        this.reservoirFullUnits = "180";
                }
            }


            String pumpFrequency = SP.getString("pref_medtronic_frequency", null);

            if (pumpFrequency == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_frequency_not_set);
                return;
            } else {
                if (!pumpFrequency.equals(frequencies[0]) && !pumpFrequency.equals(frequencies[1])) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_frequency_invalid);
                    return;
                } else {
                    this.pumpFrequency = pumpFrequency;
                    this.isFrequencyUS = pumpFrequency.equals(frequencies[0]);
                }
            }


            String rileyLinkAddress = SP.getString("pref_medtronic_rileylink_mac", null);

            if (rileyLinkAddress == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_rileylink_address_invalid);
                return;
            } else {
                if (!rileyLinkAddress.matches(regexMac)) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_rileylink_address_invalid);
                } else {
                    this.rileyLinkAddress = rileyLinkAddress;
                }
            }


            String value = SP.getString("pref_medtronic_max_bolus", "25");

            maxBolus = Integer.parseInt(value);

            if (maxBolus> 25)
            {
                SP.putString("pref_medtronic_max_bolus", "25");
            }

            value = SP.getString("pref_medtronic_max_basal", "35");

            maxBasal = Integer.parseInt(value);

            if (maxBasal> 35)
            {
                SP.putString("pref_medtronic_max_basal", "35");
            }

        }
        catch(Exception ex)
        {
            this.errorDescription = ex.getMessage();
            LOG.error("Error on Verification: " + ex.getMessage(), ex);
        }
    }


    public String getErrorInfo()
    {
        verifyConfiguration();

        return (this.errorDescription==null) ? "-" : this.errorDescription;
    }


    @Override
    public void refreshConfiguration() {
        verifyConfiguration();
    }


}
