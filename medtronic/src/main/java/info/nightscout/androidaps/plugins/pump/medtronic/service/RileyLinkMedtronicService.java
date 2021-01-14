package info.nightscout.androidaps.plugins.pump.medtronic.service;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.R;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIPostprocessor;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
@Singleton
public class RileyLinkMedtronicService extends RileyLinkService {

    @Inject MedtronicPumpPlugin medtronicPumpPlugin;
    @Inject MedtronicUtil medtronicUtil;
    @Inject MedtronicUIPostprocessor medtronicUIPostprocessor;
    @Inject MedtronicPumpStatus medtronicPumpStatus;
    @Inject RFSpy rfSpy;
    @Inject MedtronicCommunicationManager medtronicCommunicationManager;

    private MedtronicUIComm medtronicUIComm;
    private final IBinder mBinder = new LocalBinder();

    private boolean serialChanged = false;
    private String[] frequencies;
    private String rileyLinkAddress = null;
    private boolean rileyLinkAddressChanged = false;
    private RileyLinkEncodingType encodingType;
    private boolean encodingChanged = false;
    private boolean inPreInit = true;


    // This empty constructor must be kept, otherwise dagger injection might break!
    @Inject
    public RileyLinkMedtronicService() {
    }


    @Override public void onCreate() {
        super.onCreate();
        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkMedtronicService newly created");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        aapsLogger.warn(LTag.PUMPCOMM, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public RileyLinkEncodingType getEncoding() {
        return RileyLinkEncodingType.FourByteSixByteLocal;
    }


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public void initRileyLinkServiceData() {

        frequencies = new String[2];
        frequencies[0] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_us_ca);
        frequencies[1] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_worldwide);

        rileyLinkServiceData.targetDevice = RileyLinkTargetDevice.MedtronicPump;

        setPumpIDString(sp.getString(MedtronicConst.Prefs.PumpSerial, "000000"));

        // get most recently used RileyLink address and name
        rileyLinkServiceData.rileyLinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");
        rileyLinkServiceData.rileyLinkName = sp.getString(RileyLinkConst.Prefs.RileyLinkName, "");

        rfspy.startReader();

        medtronicUIComm = new MedtronicUIComm(injector, aapsLogger, medtronicUtil, medtronicUIPostprocessor, medtronicCommunicationManager);

        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkMedtronicService newly constructed");
    }

    public MedtronicCommunicationManager getDeviceCommunicationManager() {
        return this.medtronicCommunicationManager;
    }


    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.medtronicPumpStatus.setPumpDeviceState(pumpDeviceState);
    }


    public MedtronicUIComm getMedtronicUIComm() {
        return medtronicUIComm;
    }

    public void setPumpIDString(String pumpID) {
        if (pumpID.length() != 6) {
            aapsLogger.error("setPumpIDString: invalid pump id string: " + pumpID);
            return;
        }

        byte[] pumpIDBytes = ByteUtil.fromHexString(pumpID);

        if (pumpIDBytes == null) {
            aapsLogger.error("Invalid pump ID? - PumpID is null.");

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpIDBytes.length != 3) {
            aapsLogger.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpID.equals("000000")) {
            aapsLogger.error("Using pump ID " + pumpID);

            rileyLinkServiceData.setPumpID(pumpID, new byte[]{0, 0, 0});

        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "Using pump ID " + pumpID);

            String oldId = rileyLinkServiceData.pumpID;

            rileyLinkServiceData.setPumpID(pumpID, pumpIDBytes);

            if (oldId != null && !oldId.equals(pumpID)) {
                medtronicUtil.setMedtronicPumpModel(null); // if we change pumpId, model probably changed too
            }

            return;
        }

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.InvalidConfiguration);

        // LOG.info("setPumpIDString: saved pumpID " + idString);
    }

    public class LocalBinder extends Binder {

        public RileyLinkMedtronicService getServiceInstance() {
            return RileyLinkMedtronicService.this;
        }
    }


    /* private functions */

    // PumpInterface - REMOVE

    public boolean isInitialized() {
        return rileyLinkServiceData.rileyLinkServiceState.isReady();
    }


    public boolean verifyConfiguration(boolean forceRileyLinkAddressRenewal) {
        try {
            String regexSN = "[0-9]{6}";
            String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";

            medtronicPumpStatus.errorDescription = "-";

            String serialNr = sp.getStringOrNull(MedtronicConst.Prefs.PumpSerial, null);

            if (serialNr == null) {
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_not_set);
                return false;
            } else {
                if (!serialNr.matches(regexSN)) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_invalid);
                    return false;
                } else {
                    if (!serialNr.equals(medtronicPumpStatus.serialNumber)) {
                        medtronicPumpStatus.serialNumber = serialNr;
                        serialChanged = true;
                    }
                }
            }

            String pumpTypePref = sp.getStringOrNull(MedtronicConst.Prefs.PumpType, null);

            if (pumpTypePref == null) {
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_not_set);
                return false;
            } else {
                String pumpTypePart = pumpTypePref.substring(0, 3);

                if (!pumpTypePart.matches("[0-9]{3}")) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_invalid);
                    return false;
                } else {
                    PumpType pumpType = medtronicPumpStatus.getMedtronicPumpMap().get(pumpTypePart);
                    medtronicPumpStatus.medtronicDeviceType = medtronicPumpStatus.getMedtronicDeviceTypeMap().get(pumpTypePart);
                    medtronicPumpPlugin.setPumpType(pumpType);

                    if (pumpTypePart.startsWith("7"))
                        medtronicPumpStatus.reservoirFullUnits = 300;
                    else
                        medtronicPumpStatus.reservoirFullUnits = 176;
                }
            }

            String pumpFrequency = sp.getStringOrNull(MedtronicConst.Prefs.PumpFrequency, null);

            if (pumpFrequency == null) {
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_frequency_not_set);
                return false;
            } else {
                if (!pumpFrequency.equals(frequencies[0]) && !pumpFrequency.equals(frequencies[1])) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_frequency_invalid);
                    return false;
                } else {
                    medtronicPumpStatus.pumpFrequency = pumpFrequency;
                    boolean isFrequencyUS = pumpFrequency.equals(frequencies[0]);

                    RileyLinkTargetFrequency newTargetFrequency = isFrequencyUS ? //
                            RileyLinkTargetFrequency.Medtronic_US
                            : RileyLinkTargetFrequency.Medtronic_WorldWide;

                    if (rileyLinkServiceData.rileyLinkTargetFrequency != newTargetFrequency) {
                        rileyLinkServiceData.rileyLinkTargetFrequency = newTargetFrequency;
                    }

                }
            }

            String rileyLinkAddress = sp.getStringOrNull(RileyLinkConst.Prefs.RileyLinkAddress, null);

            if (rileyLinkAddress == null) {
                aapsLogger.debug(LTag.PUMP, "RileyLink address invalid: null");
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_rileylink_address_invalid);
                return false;
            } else {
                if (!rileyLinkAddress.matches(regexMac)) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_rileylink_address_invalid);
                    aapsLogger.debug(LTag.PUMP, "RileyLink address invalid: {}", rileyLinkAddress);
                } else {
                    if (!rileyLinkAddress.equals(this.rileyLinkAddress)) {
                        this.rileyLinkAddress = rileyLinkAddress;
                        rileyLinkAddressChanged = true;
                    }
                }
            }

            double maxBolusLcl = checkParameterValue(MedtronicConst.Prefs.MaxBolus, "25.0", 25.0d);

            if (medtronicPumpStatus.maxBolus == null || !medtronicPumpStatus.maxBolus.equals(maxBolusLcl)) {
                medtronicPumpStatus.maxBolus = maxBolusLcl;

                //LOG.debug("Max Bolus from AAPS settings is " + maxBolus);
            }

            double maxBasalLcl = checkParameterValue(MedtronicConst.Prefs.MaxBasal, "35.0", 35.0d);

            if (medtronicPumpStatus.maxBasal == null || !medtronicPumpStatus.maxBasal.equals(maxBasalLcl)) {
                medtronicPumpStatus.maxBasal = maxBasalLcl;

                //LOG.debug("Max Basal from AAPS settings is " + maxBasal);
            }


            String encodingTypeStr = sp.getStringOrNull(MedtronicConst.Prefs.Encoding, null);

            if (encodingTypeStr == null) {
                return false;
            }

            RileyLinkEncodingType newEncodingType = RileyLinkEncodingType.getByDescription(encodingTypeStr, resourceHelper);

            if (encodingType == null) {
                encodingType = newEncodingType;
            } else if (encodingType != newEncodingType) {
                encodingType = newEncodingType;
                encodingChanged = true;
            }

            String batteryTypeStr = sp.getStringOrNull(MedtronicConst.Prefs.BatteryType, null);

            if (batteryTypeStr == null)
                return false;

            BatteryType batteryType = medtronicPumpStatus.getBatteryTypeByDescription(batteryTypeStr);

            if (medtronicPumpStatus.batteryType != batteryType) {
                medtronicPumpStatus.batteryType = batteryType;
            }

            //String bolusDebugEnabled = sp.getStringOrNull(MedtronicConst.Prefs.BolusDebugEnabled, null);
            //boolean bolusDebug = bolusDebugEnabled != null && bolusDebugEnabled.equals(resourceHelper.gs(R.string.common_on));
            //MedtronicHistoryData.doubleBolusDebug = bolusDebug;

            reconfigureService(forceRileyLinkAddressRenewal);

            return true;

        } catch (Exception ex) {
            medtronicPumpStatus.errorDescription = ex.getMessage();
            aapsLogger.error(LTag.PUMP, "Error on Verification: " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean reconfigureService(boolean forceRileyLinkAddressRenewal) {

        if (!inPreInit) {

            if (serialChanged) {
                setPumpIDString(medtronicPumpStatus.serialNumber); // short operation
                serialChanged = false;
            }

            if (rileyLinkAddressChanged || forceRileyLinkAddressRenewal) {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this);
                rileyLinkAddressChanged = false;
            }

            if (encodingChanged) {
                changeRileyLinkEncoding(encodingType);
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
        double val;

        String value = sp.getString(key, defaultValue);

        try {
            val = Double.parseDouble(value);
        } catch (Exception ex) {
            aapsLogger.error("Error parsing setting: {}, value found {}", key, value);
            val = defaultValueDouble;
        }

        if (val > defaultValueDouble) {
            sp.putString(key, defaultValue);
            val = defaultValueDouble;
        }

        return val;
    }

    public boolean setNotInPreInit() {
        this.inPreInit = false;

        return reconfigureService(false);
    }
}
