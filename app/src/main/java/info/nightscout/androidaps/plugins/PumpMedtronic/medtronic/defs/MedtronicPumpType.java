package info.nightscout.androidaps.plugins.PumpMedtronic.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by andy on 4/28/18.
 */

public enum MedtronicPumpType {

    Unknown("xxx", 0.1f, 0.1f, 200, 35.0d, 0),
    Medtronic_512("512", 0.05f, 0.1f, 200, 35.0d, 180),
    Medtronic_712("712", 0.05f, 0.1f, 200, 35.0d, 300),

    Medtronic_515("515", 0.05f, 0.1f, 200, 35.0d, 180),
    Medtronic_715("715", 0.05f, 0.1f, 200, 35.0d, 300),
    Medtronic_522("522", 0.05f, 0.1f, 200, 35.0d, 180),
    Medtronic_722("722", 0.05f, 0.1f, 200, 35.0d, 300),

    Medtronic_523("523", 0.1f, 0.1f, 200, 35.0d, 180),
    Medtronic_723("723", 0.1f, 0.1f, 200, 35.0d, 300),
    Medtronic_554("554", 0.1f, 0.1f, 200, 35.0d, 180),
    Medtronic_754("754", 0.1f, 0.1f, 200, 35.0d, 300),
    ;

    //0.025 units (for rates between 0.025-0.975 u/h)
    //0.05 units (for rates between 1-9.95 u/h)
    //0.1 units (for rates of 10 u/h or more); (or 1%)


//
//
//    Minimed_511(10003, "Minimed 511", "mm_515_715.jpg", "INSTRUCTIONS_MINIMED", MinimedDeviceType.Minimed_511,
//                DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed, DeviceHandlerType.MinimedPumpHandler,
//                DevicePortParameterType.PackedParameters, DeviceConnectionProtocol.Serial_USBBridge,
//                DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0, PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_512_712(10004, "Minimed 512/712", "mm_515_715.jpg", "INSTRUCTIONS_MINIMED",
//                    MinimedDeviceType.Minimed_512_712, DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed,
//                    DeviceHandlerType.MinimedPumpHandler, DevicePortParameterType.PackedParameters,
//                    DeviceConnectionProtocol.USB_Hid, DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0,
//                    PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_515_715(10005, "Minimed 515/715", "mm_515_715.jpg", "INSTRUCTIONS_MINIMED",
//                    MinimedDeviceType.Minimed_515_715, DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed,
//                    DeviceHandlerType.MinimedPumpHandler, DevicePortParameterType.PackedParameters,
//                    DeviceConnectionProtocol.Serial_USBBridge, DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0,
//                    PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_522_722(10006, "Minimed 522/722", "mm_522_722.jpg", "INSTRUCTIONS_MINIMED",
//                    MinimedDeviceType.Minimed_522_722, DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed,
//                    DeviceHandlerType.MinimedPumpHandler, DevicePortParameterType.PackedParameters,
//                    DeviceConnectionProtocol.Serial_USBBridge, DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0,
//                    PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_523_723(10007, "Minimed 523/723", "mm_522_722.jpg", "INSTRUCTIONS_MINIMED",
//                    MinimedDeviceType.Minimed_523_723, DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed,
//                    DeviceHandlerType.MinimedPumpHandler, DevicePortParameterType.PackedParameters,
//                    DeviceConnectionProtocol.Serial_USBBridge, DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0,
//                    PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_553_753_Revel(10008, "Minimed 553/753 (Revel)", "mm_554_veo.jpg", "INSTRUCTIONS_MINIMED",
//                          MinimedDeviceType.Minimed_553_753_Revel, DeviceImplementationStatus.Planned,
//                          DeviceCompanyDefinition.Minimed, DeviceHandlerType.MinimedPumpHandler,
//                          DevicePortParameterType.PackedParameters, DeviceConnectionProtocol.Serial_USBBridge,
//                          DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0, PumpProfileDefinition.MinimedProfile), // TODO
//
//    Minimed_554_754_Veo(10009, "Minimed 554/754 (Veo)", "mm_554_veo.jpg", "INSTRUCTIONS_MINIMED",
//                        MinimedDeviceType.Minimed_554_754_Veo, DeviceImplementationStatus.Planned, DeviceCompanyDefinition.Minimed,
//                        DeviceHandlerType.MinimedPumpHandler, DevicePortParameterType.PackedParameters,
//                        DeviceConnectionProtocol.Serial_USBBridge, DeviceProgressStatus.Special, "", 0.1f, 0.1f, null, -1, 0,
//                        PumpProfileDefinition.MinimedProfile), // TODO
//





    private static Map<String,MedtronicPumpType> mapByCode;

    private String code;
    private int reservoir;
    private double basalStep;
    private double bolusStep;
    private int maxTbrPercent;
    private double maxTbrUnit;

    static
    {
        mapByCode = new HashMap<>();

        for (MedtronicPumpType medtronicPumpType : values()) {
            mapByCode.put(medtronicPumpType.getCode(), medtronicPumpType);
        }
    }

    MedtronicPumpType(String code, double basalStep, double bolusStep, int maxTbrPercent, double maxTbrUnit, int reservoir)
    {
        this.code = code;
        this.reservoir = reservoir;
        this.basalStep = basalStep;
        this.bolusStep = bolusStep;
        this.maxTbrPercent = maxTbrPercent;
        this.maxTbrUnit = maxTbrUnit;
    }


    public static MedtronicPumpType getByCode(String code) {

        if (mapByCode.containsKey(code))
        {
            return mapByCode.get(code);
        }
        else
        {
            return MedtronicPumpType.Unknown;
        }
    }

    public String getCode() {
        return code;
    }

    public int getReservoir() {
        return reservoir;
    }

    public double getBasalStep() {
        return basalStep;
    }

    public double getBolusStep() {
        return bolusStep;
    }

    public int getMaxTbrPercent() {
        return maxTbrPercent;
    }

    public double getMaxTbrUnit() {
        return maxTbrUnit;
    }
}
