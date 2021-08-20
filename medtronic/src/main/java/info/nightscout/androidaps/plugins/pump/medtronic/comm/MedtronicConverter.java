package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import org.joda.time.IllegalFieldValueException;
import org.joda.time.LocalDateTime;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpConfigurationGroup;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 5/9/18.
 * High level decoder for data returned through MedtroniUIComm
 */

@Singleton
public class MedtronicConverter {

    private final AAPSLogger aapsLogger;
    private final MedtronicUtil medtronicUtil;

    @Inject
    public MedtronicConverter(
            AAPSLogger aapsLogger,
            MedtronicUtil medtronicUtil
    ) {
        this.aapsLogger = aapsLogger;
        this.medtronicUtil = medtronicUtil;
    }

    Object convertResponse(PumpType pumpType, MedtronicCommandType commandType, byte[] rawContent) {

        if ((rawContent == null || rawContent.length < 1) && commandType != MedtronicCommandType.PumpModel) {
            aapsLogger.warn(LTag.PUMPCOMM, "Content is empty or too short, no data to convert (type={},isNull={},length={})",
                    commandType.name(), rawContent == null, rawContent == null ? "-" : rawContent.length);
            return null;
        }

        aapsLogger.debug(LTag.PUMPCOMM, "Raw response before convert: " + ByteUtil.shortHexString(rawContent));

        switch (commandType) {

            case PumpModel: {
                return decodeModel(rawContent);
            }

            case GetRealTimeClock: {
                return decodeTime(rawContent);
            }

            case GetRemainingInsulin: {
                return decodeRemainingInsulin(rawContent);
            }

            case GetBatteryStatus: {
                return decodeBatteryStatus(rawContent); // 1
            }

            case GetBasalProfileSTD:
            case GetBasalProfileA:
            case GetBasalProfileB: {
                return decodeBasalProfile(pumpType, rawContent);

            }

            case ReadTemporaryBasal: {
                return new TempBasalPair(aapsLogger, rawContent); // 5
            }

            case Settings_512: {
                return decodeSettingsLoop(rawContent);
            }

            case Settings: {
                return decodeSettingsLoop(rawContent);
            }

            case SetBolus: {
                return rawContent; // 1
            }

            default: {
                throw new RuntimeException("Unsupported command Type: " + commandType);
            }

        }

    }


    private BasalProfile decodeBasalProfile(PumpType pumpType, byte[] rawContent) {

        BasalProfile basalProfile = new BasalProfile(aapsLogger, rawContent);

        return basalProfile.verify(pumpType) ? basalProfile : null;
    }


    private MedtronicDeviceType decodeModel(byte[] rawContent) {

        if ((rawContent == null || rawContent.length < 4)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Error reading PumpModel, returning Unknown_Device");
            return MedtronicDeviceType.Unknown_Device;
        }

        String rawModel = StringUtil.fromBytes(ByteUtil.substring(rawContent, 1, 3));
        MedtronicDeviceType pumpModel = MedtronicDeviceType.getByDescription(rawModel);
        aapsLogger.debug(LTag.PUMPCOMM, "PumpModel: [raw={}, resolved={}]", rawModel, pumpModel.name());

        if (pumpModel != MedtronicDeviceType.Unknown_Device) {
            if (!medtronicUtil.isModelSet()) {
                medtronicUtil.setMedtronicPumpModel(pumpModel);
            }
        }

        return pumpModel;
    }


    private BatteryStatusDTO decodeBatteryStatus(byte[] rawData) {
        // 00 7C 00 00

        BatteryStatusDTO batteryStatus = new BatteryStatusDTO();

        int status = rawData[0];

        if (status == 0) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal;
        } else if (status == 1) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low;
        } else if (status == 2) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown;
        }

        if (rawData.length > 1) {

            Double d = null;

            // if response in 3 bytes then we add additional information
            if (rawData.length == 2) {
                d = (rawData[1] * 1.0d) / 100.0d;
            } else {
                d = (ByteUtil.toInt(rawData[1], rawData[2]) * 1.0d) / 100.0d;
            }

            batteryStatus.voltage = d;
            batteryStatus.extendedDataReceived = true;
        }

        return batteryStatus;
    }


    private Float decodeRemainingInsulin(byte[] rawData) {
        int startIdx = 0;

        MedtronicDeviceType pumpModel = medtronicUtil.getMedtronicPumpModel();

        int strokes = pumpModel == null ? 10 : pumpModel.getBolusStrokes();

        if (strokes == 40) {
            startIdx = 2;
        }

        int reqLength = startIdx+1;
        float value = 0;

        if (reqLength >= rawData.length) {
            value = rawData[startIdx] / (1.0f * strokes);
        } else {
            value = ByteUtil.toInt(rawData[startIdx], rawData[startIdx + 1]) / (1.0f * strokes);
        }

        aapsLogger.debug(LTag.PUMPCOMM, "Remaining insulin: " + value);
        return value;
    }


    private LocalDateTime decodeTime(byte[] rawContent) {

        int hours = ByteUtil.asUINT8(rawContent[0]);
        int minutes = ByteUtil.asUINT8(rawContent[1]);
        int seconds = ByteUtil.asUINT8(rawContent[2]);
        int year = (ByteUtil.asUINT8(rawContent[4]) & 0x3f) + 1984;
        int month = ByteUtil.asUINT8(rawContent[5]);
        int day = ByteUtil.asUINT8(rawContent[6]);
        try {
            LocalDateTime pumpTime = new LocalDateTime(year, month, day, hours, minutes, seconds);
            return pumpTime;
        } catch (IllegalFieldValueException e) {
            aapsLogger.error(LTag.PUMPCOMM,
                    "decodeTime: Failed to parse pump time value: year=%d, month=%d, hours=%d, minutes=%d, seconds=%d",
                    year, month, day, hours, minutes, seconds);
            return null;
        }

    }


    private Map<String, PumpSettingDTO> decodeSettingsLoop(byte[] rd) {

        Map<String, PumpSettingDTO> map = new HashMap<>();

        addSettingToMap("PCFG_MAX_BOLUS", "" + decodeMaxBolus(rd), PumpConfigurationGroup.Bolus, map);
        addSettingToMap(
                "PCFG_MAX_BASAL",
                ""
                        + decodeBasalInsulin(ByteUtil.makeUnsignedShort(rd[getSettingIndexMaxBasal()],
                        rd[getSettingIndexMaxBasal() + 1])), PumpConfigurationGroup.Basal, map);
        addSettingToMap("CFG_BASE_CLOCK_MODE", rd[getSettingIndexTimeDisplayFormat()] == 0 ? "12h" : "24h",
                PumpConfigurationGroup.General, map);

        addSettingToMap("PCFG_BASAL_PROFILES_ENABLED", parseResultEnable(rd[10]), PumpConfigurationGroup.Basal, map);

        if (rd[10] == 1) {
            String patt;
            switch (rd[11]) {
                case 0:
                    patt = "STD";
                    break;

                case 1:
                    patt = "A";
                    break;

                case 2:
                    patt = "B";
                    break;

                default:
                    patt = "???";
                    break;
            }

            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", patt, PumpConfigurationGroup.Basal, map);

        } else {
            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", "STD", PumpConfigurationGroup.Basal, map);
        }

        addSettingToMap("PCFG_TEMP_BASAL_TYPE", rd[14] != 0 ? "Percent" : "Units", PumpConfigurationGroup.Basal, map);

        return map;
    }


    private Map<String, PumpSettingDTO> decodeSettings512(byte[] rd) {

        Map<String, PumpSettingDTO> map = new HashMap<>();

        addSettingToMap("PCFG_AUTOOFF_TIMEOUT", "" + rd[0], PumpConfigurationGroup.General, map);

        if (rd[1] == 4) {
            addSettingToMap("PCFG_ALARM_MODE", "Silent", PumpConfigurationGroup.Sound, map);
        } else {
            addSettingToMap("PCFG_ALARM_MODE", "Normal", PumpConfigurationGroup.Sound, map);
            addSettingToMap("PCFG_ALARM_BEEP_VOLUME", "" + rd[1], PumpConfigurationGroup.Sound, map);
        }

        addSettingToMap("PCFG_AUDIO_BOLUS_ENABLED", parseResultEnable(rd[2]), PumpConfigurationGroup.Bolus, map);

        if (rd[2] == 1) {
            addSettingToMap("PCFG_AUDIO_BOLUS_STEP_SIZE", "" + decodeBolusInsulin(ByteUtil.asUINT8(rd[3])),
                    PumpConfigurationGroup.Bolus, map);
        }

        addSettingToMap("PCFG_VARIABLE_BOLUS_ENABLED", parseResultEnable(rd[4]), PumpConfigurationGroup.Bolus, map);
        addSettingToMap("PCFG_MAX_BOLUS", "" + decodeMaxBolus(rd), PumpConfigurationGroup.Bolus, map);
        addSettingToMap(
                "PCFG_MAX_BASAL",
                ""
                        + decodeBasalInsulin(ByteUtil.makeUnsignedShort(rd[getSettingIndexMaxBasal()],
                        rd[getSettingIndexMaxBasal() + 1])), PumpConfigurationGroup.Basal, map);
        addSettingToMap("CFG_BASE_CLOCK_MODE", rd[getSettingIndexTimeDisplayFormat()] == 0 ? "12h" : "24h",
                PumpConfigurationGroup.General, map);

        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher)) {
            addSettingToMap("PCFG_INSULIN_CONCENTRATION", "" + (rd[9] == 0 ? 50 : 100), PumpConfigurationGroup.Insulin,
                    map);
//            LOG.debug("Insulin concentration: " + rd[9]);
        } else {
            addSettingToMap("PCFG_INSULIN_CONCENTRATION", "" + (rd[9] != 0 ? 50 : 100), PumpConfigurationGroup.Insulin,
                    map);
//            LOG.debug("Insulin concentration: " + rd[9]);
        }
        addSettingToMap("PCFG_BASAL_PROFILES_ENABLED", parseResultEnable(rd[10]), PumpConfigurationGroup.Basal, map);

        if (rd[10] == 1) {
            String patt;
            switch (rd[11]) {
                case 0:
                    patt = "STD";
                    break;

                case 1:
                    patt = "A";
                    break;

                case 2:
                    patt = "B";
                    break;

                default:
                    patt = "???";
                    break;
            }

            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", patt, PumpConfigurationGroup.Basal, map);

        }

        addSettingToMap("CFG_MM_RF_ENABLED", parseResultEnable(rd[12]), PumpConfigurationGroup.General, map);
        addSettingToMap("CFG_MM_BLOCK_ENABLED", parseResultEnable(rd[13]), PumpConfigurationGroup.General, map);

        addSettingToMap("PCFG_TEMP_BASAL_TYPE", rd[14] != 0 ? "Percent" : "Units", PumpConfigurationGroup.Basal, map);

        if (rd[14] == 1) {
            addSettingToMap("PCFG_TEMP_BASAL_PERCENT", "" + rd[15], PumpConfigurationGroup.Basal, map);
        }

        addSettingToMap("CFG_PARADIGM_LINK_ENABLE", parseResultEnable(rd[16]), PumpConfigurationGroup.General, map);

        decodeInsulinActionSetting(rd, map);

        return map;
    }


    private void addSettingToMap(String key, String value, PumpConfigurationGroup group, Map<String, PumpSettingDTO> map) {
        map.put(key, new PumpSettingDTO(key, value, group));
    }


    public Map<String, PumpSettingDTO> decodeSettings(byte[] rd) {
        Map<String, PumpSettingDTO> map = decodeSettings512(rd);

        addSettingToMap("PCFG_MM_RESERVOIR_WARNING_TYPE_TIME", rd[18] != 0 ? "PCFG_MM_RESERVOIR_WARNING_TYPE_TIME"
                : "PCFG_MM_RESERVOIR_WARNING_TYPE_UNITS", PumpConfigurationGroup.Other, map);

        addSettingToMap("PCFG_MM_SRESERVOIR_WARNING_POINT", "" + ByteUtil.asUINT8(rd[19]),
                PumpConfigurationGroup.Other, map);

        addSettingToMap("CFG_MM_KEYPAD_LOCKED", parseResultEnable(rd[20]), PumpConfigurationGroup.Other, map);

        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher)) {

            addSettingToMap("PCFG_BOLUS_SCROLL_STEP_SIZE", "" + rd[21], PumpConfigurationGroup.Bolus, map);
            addSettingToMap("PCFG_CAPTURE_EVENT_ENABLE", parseResultEnable(rd[22]), PumpConfigurationGroup.Other, map);
            addSettingToMap("PCFG_OTHER_DEVICE_ENABLE", parseResultEnable(rd[23]), PumpConfigurationGroup.Other, map);
            addSettingToMap("PCFG_OTHER_DEVICE_PAIRED_STATE", parseResultEnable(rd[24]), PumpConfigurationGroup.Other,
                    map);
        }

        return map;
    }


    private String parseResultEnable(int i) {
        switch (i) {
            case 0:
                return "No";
            case 1:
                return "Yes";
            default:
                return "???";
        }
    }


    private float getStrokesPerUnit(boolean isBasal) {
        return isBasal ? 40.0f : 10; // pumpModel.getBolusStrokes();
    }


    // 512
    private void decodeInsulinActionSetting(byte[] ai, Map<String, PumpSettingDTO> map) {
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_512_712)) {
            addSettingToMap("PCFG_INSULIN_ACTION_TYPE", (ai[17] != 0 ? "Regular" : "Fast"),
                    PumpConfigurationGroup.Insulin, map);
        } else {
            int i = ai[17];
            String s;

            if ((i == 0) || (i == 1)) {
                s = ai[17] != 0 ? "Regular" : "Fast";
            } else {
                if (i == 15)
                    s = "Unset";
                else
                    s = "Curve: " + i;
            }

            addSettingToMap("PCFG_INSULIN_ACTION_TYPE", s, PumpConfigurationGroup.Insulin, map);
        }
    }


    private double decodeBasalInsulin(int i) {
        return (double) i / (double) getStrokesPerUnit(true);
    }


    private double decodeBolusInsulin(int i) {

        return (double) i / (double) getStrokesPerUnit(false);
    }


    private int getSettingIndexMaxBasal() {
        return is523orHigher() ? 7 : 6;
    }


    private int getSettingIndexTimeDisplayFormat() {
        return is523orHigher() ? 9 : 8;
    }


    private double decodeMaxBolus(byte[] ai) {
        return is523orHigher() ? decodeBolusInsulin(ByteUtil.toInt(ai[5], ai[6])) : decodeBolusInsulin(ByteUtil
                .asUINT8(ai[5]));
    }


    private boolean is523orHigher() {
        return (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher));
    }
}
