package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import info.nightscout.androidaps.plugins.PumpMedtronic.defs.PumpConfigurationGroup;

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

}
