package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpConfigurationGroup;

/**
 * Created by andy on 6/6/18.
 */

public class PumpSettingDTO {

    public String key;
    public String value;

    PumpConfigurationGroup configurationGroup;


    public PumpSettingDTO(String key, String value, PumpConfigurationGroup configurationGroup) {
        this.key = key;
        this.value = value;
        this.configurationGroup = configurationGroup;
    }


    @Override
    public String toString() {
        return "PumpSettingDTO [key=" + key + ",value=" + value + ",group=" + configurationGroup.name() + "]";
    }

}
