package info.nightscout.androidaps.plugins.pump.medtronic.driver;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by andy on 4/28/18.
 */

public class MedtronicPumpStatus extends PumpStatus {

    private static Logger LOG = LoggerFactory.getLogger(L.PUMP);

    public String errorDescription = null;
    public String serialNumber;
    public String pumpFrequency = null;
    public String rileyLinkAddress = null;
    public Double maxBolus;
    public Double maxBasal;
    public boolean inPreInit = true;

    // statuses
    public RileyLinkServiceState rileyLinkServiceState = RileyLinkServiceState.NotStarted;
    public RileyLinkError rileyLinkError;
    public PumpDeviceState pumpDeviceState = PumpDeviceState.NeverContacted;
    public MedtronicDeviceType medtronicDeviceType = null;
    public double currentBasal = 0;
    public int tempBasalInProgress = 0;
    public int tempBasalRatio = 0;
    public int tempBasalRemainMin = 0;
    public Date tempBasalStart;
    public Double tempBasalAmount = 0.0d;

    // fixme
    public Integer tempBasalLength = 0;

    private String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";
    private String regexSN = "[0-9]{6}";

    private boolean serialChanged = false;
    private boolean rileyLinkAddressChanged = false;
    private boolean encodingChanged = false;
    private boolean targetFrequencyChanged = false;

    private RileyLinkEncodingType encodingType;
    private String[] frequencies;
    private boolean isFrequencyUS = false;
    private Map<String, PumpType> medtronicPumpMap = null;
    private Map<String, MedtronicDeviceType> medtronicDeviceTypeMap = null;
    private RileyLinkTargetFrequency targetFrequency;
    public BasalProfileStatus basalProfileStatus = BasalProfileStatus.NotInitialized;
    public BatteryType batteryType = BatteryType.None;


    public MedtronicPumpStatus(PumpDescription pumpDescription) {
        super(pumpDescription);
    }


    @Override
    public void initSettings() {

        this.activeProfileName = "STD";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75;

        if (this.medtronicPumpMap == null)
            createMedtronicPumpMap();

        if (this.medtronicDeviceTypeMap == null)
            createMedtronicDeviceTypeMap();

        this.lastConnection = SP.getLong(MedtronicConst.Statistics.LastGoodPumpCommunicationTime, 0L);
        this.lastDataTime = new LocalDateTime(this.lastConnection);
    }


    private void createMedtronicDeviceTypeMap() {
        medtronicDeviceTypeMap = new HashMap<>();
        medtronicDeviceTypeMap.put("512", MedtronicDeviceType.Medtronic_512);
        medtronicDeviceTypeMap.put("712", MedtronicDeviceType.Medtronic_712);
        medtronicDeviceTypeMap.put("515", MedtronicDeviceType.Medtronic_515);
        medtronicDeviceTypeMap.put("715", MedtronicDeviceType.Medtronic_715);

        medtronicDeviceTypeMap.put("522", MedtronicDeviceType.Medtronic_522);
        medtronicDeviceTypeMap.put("722", MedtronicDeviceType.Medtronic_722);
        medtronicDeviceTypeMap.put("523", MedtronicDeviceType.Medtronic_523_Revel);
        medtronicDeviceTypeMap.put("723", MedtronicDeviceType.Medtronic_723_Revel);
        medtronicDeviceTypeMap.put("554", MedtronicDeviceType.Medtronic_554_Veo);
        medtronicDeviceTypeMap.put("754", MedtronicDeviceType.Medtronic_754_Veo);
    }


    private void createMedtronicPumpMap() {

        medtronicPumpMap = new HashMap<>();
        medtronicPumpMap.put("512", PumpType.Medtronic_512_712);
        medtronicPumpMap.put("712", PumpType.Medtronic_512_712);
        medtronicPumpMap.put("515", PumpType.Medtronic_515_715);
        medtronicPumpMap.put("715", PumpType.Medtronic_515_715);

        medtronicPumpMap.put("522", PumpType.Medtronic_522_722);
        medtronicPumpMap.put("722", PumpType.Medtronic_522_722);
        medtronicPumpMap.put("523", PumpType.Medtronic_523_723_Revel);
        medtronicPumpMap.put("723", PumpType.Medtronic_523_723_Revel);
        medtronicPumpMap.put("554", PumpType.Medtronic_554_754_Veo);
        medtronicPumpMap.put("754", PumpType.Medtronic_554_754_Veo);

        frequencies = new String[2];
        frequencies[0] = MainApp.gs(R.string.key_medtronic_pump_frequency_us_ca);
        frequencies[1] = MainApp.gs(R.string.key_medtronic_pump_frequency_worldwide);
    }


    public boolean verifyConfiguration() {
        try {

            // FIXME don't reload information several times
            if (this.medtronicPumpMap == null)
                createMedtronicPumpMap();

            if (this.medtronicDeviceTypeMap == null)
                createMedtronicDeviceTypeMap();

            this.errorDescription = "-";

            String serialNr = SP.getString(MedtronicConst.Prefs.PumpSerial, null);

            if (serialNr == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_serial_not_set);
                return false;
            } else {
                if (!serialNr.matches(regexSN)) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_serial_invalid);
                    return false;
                } else {
                    if (!serialNr.equals(this.serialNumber)) {
                        this.serialNumber = serialNr;
                        serialChanged = true;
                    }
                }
            }

            String pumpType = SP.getString(MedtronicConst.Prefs.PumpType, null);

            if (pumpType == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_type_not_set);
                return false;
            } else {
                String pumpTypePart = pumpType.substring(0, 3);

                if (!pumpTypePart.matches("[0-9]{3}")) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_type_invalid);
                    return false;
                } else {
                    this.pumpType = medtronicPumpMap.get(pumpTypePart);
                    this.medtronicDeviceType = medtronicDeviceTypeMap.get(pumpTypePart);
                    this.pumpDescription.setPumpDescription(this.pumpType);

                    if (pumpTypePart.startsWith("7"))
                        this.reservoirFullUnits = 300;
                    else
                        this.reservoirFullUnits = 176;
                }
            }

            String pumpFrequency = SP.getString(MedtronicConst.Prefs.PumpFrequency, null);

            if (pumpFrequency == null) {
                this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_frequency_not_set);
                return false;
            } else {
                if (!pumpFrequency.equals(frequencies[0]) && !pumpFrequency.equals(frequencies[1])) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_pump_frequency_invalid);
                    return false;
                } else {
                    this.pumpFrequency = pumpFrequency;
                    this.isFrequencyUS = pumpFrequency.equals(frequencies[0]);

                    RileyLinkTargetFrequency newTargetFrequency = this.isFrequencyUS ? //
                            RileyLinkTargetFrequency.Medtronic_US
                            : RileyLinkTargetFrequency.Medtronic_WorldWide;

                    if (targetFrequency != newTargetFrequency) {
                        RileyLinkUtil.setRileyLinkTargetFrequency(newTargetFrequency);
                        targetFrequency = newTargetFrequency;
                        targetFrequencyChanged = true;
                    }

                }
            }

            String rileyLinkAddress = SP.getString(RileyLinkConst.Prefs.RileyLinkAddress, null);

            if (rileyLinkAddress == null) {
                if (isLogEnabled())
                    LOG.debug("RileyLink address invalid: null");
                this.errorDescription = MainApp.gs(R.string.medtronic_error_rileylink_address_invalid);
                return false;
            } else {
                if (!rileyLinkAddress.matches(regexMac)) {
                    this.errorDescription = MainApp.gs(R.string.medtronic_error_rileylink_address_invalid);
                    if (isLogEnabled())
                        LOG.debug("RileyLink address invalid: {}", rileyLinkAddress);
                } else {
                    if (!rileyLinkAddress.equals(this.rileyLinkAddress)) {
                        this.rileyLinkAddress = rileyLinkAddress;
                        rileyLinkAddressChanged = true;
                    }
                }
            }

            double maxBolusLcl = checkParameterValue(MedtronicConst.Prefs.MaxBolus, "25.0", 25.0d);

            if (maxBolus == null || !maxBolus.equals(maxBolusLcl)) {
                maxBolus = maxBolusLcl;

                //LOG.debug("Max Bolus from AAPS settings is " + maxBolus);
            }

            double maxBasalLcl = checkParameterValue(MedtronicConst.Prefs.MaxBasal, "35.0", 35.0d);

            if (maxBasal == null || !maxBasal.equals(maxBasalLcl)) {
                maxBasal = maxBasalLcl;

                //LOG.debug("Max Basal from AAPS settings is " + maxBasal);
            }


            String encodingTypeStr = SP.getString(MedtronicConst.Prefs.Encoding, null);

            if (encodingTypeStr == null) {
                return false;
            }

            RileyLinkEncodingType newEncodingType = RileyLinkEncodingType.getByDescription(encodingTypeStr);

            if (this.encodingType == null) {
                this.encodingType = newEncodingType;
            } else if (this.encodingType != newEncodingType) {
                this.encodingType = newEncodingType;
                this.encodingChanged = true;
            }

            String batteryTypeStr = SP.getString(MedtronicConst.Prefs.BatteryType, null);

            if (batteryTypeStr == null)
                return false;

            BatteryType batteryType = BatteryType.getByDescription(batteryTypeStr);

            if (this.batteryType != batteryType) {
                this.batteryType = batteryType;
                MedtronicUtil.setBatteryType(this.batteryType);
            }

            String bolusDebugEnabled = SP.getString(MedtronicConst.Prefs.BolusDebugEnabled, null);

            boolean bolusDebug = bolusDebugEnabled != null && bolusDebugEnabled.equals(MainApp.gs(R.string.common_on));

            MedtronicHistoryData.doubleBolusDebug = bolusDebug;

            reconfigureService();

            return true;

        } catch (Exception ex) {
            this.errorDescription = ex.getMessage();
            LOG.error("Error on Verification: " + ex.getMessage(), ex);
            return false;
        }
    }


    private boolean reconfigureService() {

        if (!inPreInit && MedtronicUtil.getMedtronicService() != null) {

            if (serialChanged) {
                MedtronicUtil.getMedtronicService().setPumpIDString(this.serialNumber); // short operation
                serialChanged = false;
            }

            if (rileyLinkAddressChanged) {
                MedtronicUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet);
                rileyLinkAddressChanged = false;
            }

            if (encodingChanged) {
                RileyLinkUtil.getRileyLinkService().changeRileyLinkEncoding(encodingType);
                encodingChanged = false;
            }
        }


        // if (targetFrequencyChanged && !inPreInit && MedtronicUtil.getMedtronicService() != null) {
        // RileyLinkUtil.setRileyLinkTargetFrequency(targetFrequency);
        // // RileyLinkUtil.getRileyLinkCommunicationManager().refreshRileyLinkTargetFrequency();
        // targetFrequencyChanged = false;
        // }

        return (!rileyLinkAddressChanged && !serialChanged && !encodingChanged); // && !targetFrequencyChanged);
    }


    private double checkParameterValue(int key, String defaultValue, double defaultValueDouble) {
        double val = 0.0d;

        String value = SP.getString(key, defaultValue);

        try {
            val = Double.parseDouble(value);
        } catch (Exception ex) {
            LOG.error("Error parsing setting: {}, value found {}", key, value);
            val = defaultValueDouble;
        }

        if (val > defaultValueDouble) {
            SP.putString(key, defaultValue);
            val = defaultValueDouble;
        }

        return val;
    }


    public String getErrorInfo() {
        verifyConfiguration();

        return (this.errorDescription == null) ? "-" : this.errorDescription;
    }


    @Override
    public void refreshConfiguration() {
        verifyConfiguration();
    }


    public boolean setNotInPreInit() {
        this.inPreInit = false;

        return reconfigureService();
    }


    public double getBasalProfileForHour() {
        if (basalsByHour != null) {
            GregorianCalendar c = new GregorianCalendar();
            int hour = c.get(Calendar.HOUR_OF_DAY);

            return basalsByHour[hour];
        }

        return 0;
    }

    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}
