package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.MainApp;

public enum RileyLinkEncodingType {

    None(0x00, null), // No encoding on RL
    Manchester(0x01, null), // Manchester encoding on RL (for Omnipod)
    FourByteSixByteRileyLink(0x02, R.string.key_medtronic_pump_encoding_4b6b_rileylink), // 4b6b encoding on RL (for Medtronic)
    FourByteSixByteLocal(0x00, R.string.key_medtronic_pump_encoding_4b6b_local), // No encoding on RL, but 4b6b encoding in code
    ;

    public byte value;
    public Integer resourceId;
    public String description;

    private static Map<String, RileyLinkEncodingType> encodingTypeMap;

    static {
        encodingTypeMap = new HashMap<>();

        for (RileyLinkEncodingType encType : values()) {
            if (encType.resourceId!=null) {
                encodingTypeMap.put(MainApp.gs(encType.resourceId), encType);
            }
        }
    }


    RileyLinkEncodingType(int value) {
        this.value = (byte)value;
    }


    RileyLinkEncodingType(int value, Integer resourceId) {
        this.value = (byte)value;
        this.resourceId = resourceId;
    }

    public static RileyLinkEncodingType getByDescription(String description) {
        if (encodingTypeMap.containsKey(description)) {
            return encodingTypeMap.get(description);
        }

        return RileyLinkEncodingType.FourByteSixByteLocal;
    }

}
