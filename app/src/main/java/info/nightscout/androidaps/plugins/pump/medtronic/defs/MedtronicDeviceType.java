package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

/**
 * Taken from GNU Gluco Control diabetes management software (ggc.sourceforge.net)
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum MedtronicDeviceType {
    Unknown_Device, //

    // Pump
    // Medtronic_508_508c(null, null), //
    Medtronic_511(MedtronicConverterType.Pump511Converter, null, "511"), //

    Medtronic_512(MedtronicConverterType.Pump512Converter, null, "512"), //
    Medtronic_712(MedtronicConverterType.Pump512Converter, null, "712"), //
    Medtronic_512_712(Medtronic_512, Medtronic_712), //

    Medtronic_515(MedtronicConverterType.Pump515Converter, null, "515"), //
    Medtronic_715(MedtronicConverterType.Pump515Converter, null, "715"), //
    Medtronic_515_715(Medtronic_515, Medtronic_715), //

    Medtronic_522(MedtronicConverterType.Pump515Converter, MedtronicConverterType.CGMS522Converter, "522"), //
    Medtronic_722(MedtronicConverterType.Pump515Converter, MedtronicConverterType.CGMS522Converter, "722"), //
    Medtronic_522_722(Medtronic_522, Medtronic_722), //

    Medtronic_523_Revel(MedtronicConverterType.Pump523Converter, MedtronicConverterType.CGMS523Converter, "523"), //
    Medtronic_723_Revel(MedtronicConverterType.Pump523Converter, MedtronicConverterType.CGMS523Converter, "723"), //

    Medtronic_554_Veo(MedtronicConverterType.Pump523Converter, MedtronicConverterType.CGMS523Converter, "554"), //
    Medtronic_754_Veo(MedtronicConverterType.Pump523Converter, MedtronicConverterType.CGMS523Converter, "754"), //
    // Minimed_640G(MedtronicConverterType.Pump523Converter, MedtronicConverterType.CGMS523Converter, "640G", null),

    Medtronic_512andHigher(Medtronic_512, Medtronic_712, Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, //
    Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo), //

    Medtronic_515andHigher(Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, //
    Medtronic_554_Veo, Medtronic_754_Veo), //
    Medtronic_522andHigher(Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, //
    Medtronic_554_Veo, Medtronic_754_Veo), //
    Medtronic_523andHigher(Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, //
    Medtronic_754_Veo), //

    // Medtronic_553andHigher(Medtronic_553_Revel, Medtronic_753_Revel, Medtronic_554_Veo, Medtronic_754_Veo), //
    Medtronic_554andHigher(Medtronic_554_Veo, Medtronic_754_Veo), //

    // CGMS
    MedtronicCGMSGold(null, MedtronicConverterType.CGMS522Converter, null), //

    MedtronicGuradianRealTime(null, MedtronicConverterType.CGMS522Converter, null), //

    //
    All;

    static Map<String, MedtronicDeviceType> mapByDescription;

    static {

        mapByDescription = new HashMap<>();

        for (MedtronicDeviceType minimedDeviceType : values()) {

            if (!minimedDeviceType.isFamily) {
                mapByDescription.put(minimedDeviceType.pumpModel, minimedDeviceType);
            }
        }

    }

    MedtronicConverterType pumpConverter;
    MedtronicConverterType cgmsConverter;
    private String pumpModel;

    // String smallReservoirPump;
    // String bigReservoirPump;
    private boolean isFamily;
    private MedtronicDeviceType[] familyMembers = null;


    MedtronicDeviceType(MedtronicConverterType pumpConverter, MedtronicConverterType cgmsConverter, String pumpModel) {
        this.isFamily = false;
        this.pumpConverter = pumpConverter;
        this.cgmsConverter = cgmsConverter;

        this.pumpModel = pumpModel;
    }


    MedtronicDeviceType(MedtronicDeviceType... familyMembers) {
        this.familyMembers = familyMembers;
        this.isFamily = true;
    }


    public static boolean isSameDevice(MedtronicDeviceType deviceWeCheck, MedtronicDeviceType deviceSources) {
        if (deviceSources.isFamily) {
            for (MedtronicDeviceType mdt : deviceSources.familyMembers) {
                if (mdt == deviceWeCheck)
                    return true;
            }
        } else {
            return (deviceWeCheck == deviceSources);
        }

        return false;
    }


    public static MedtronicDeviceType getByDescription(String desc) {
        if (mapByDescription.containsKey(desc)) {
            return mapByDescription.get(desc);
        } else {
            return MedtronicDeviceType.Unknown_Device;
        }
    }


    public static boolean isLargerFormat(MedtronicDeviceType model) {
        return isSameDevice(model, Medtronic_523andHigher);
    }


    public boolean isFamily() {
        return isFamily;
    }


    public MedtronicDeviceType[] getFamilyMembers() {
        return familyMembers;
    }


    public MedtronicConverterType getCGMSConverterType() {
        return cgmsConverter;
    }


    public MedtronicConverterType getPumpConverterType() {
        return pumpConverter;
    }


    public boolean isLargerFormat() {
        return isSameDevice(this, Medtronic_523andHigher);
    }


    public int getBolusStrokes() {
        return (isLargerFormat(this)) ? 40 : 10;
    }


    public String getPumpModel() {
        return pumpModel;
    }
}
