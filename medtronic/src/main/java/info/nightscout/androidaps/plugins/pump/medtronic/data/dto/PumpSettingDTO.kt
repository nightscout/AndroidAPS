package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpConfigurationGroup

/**
 * Created by andy on 6/6/18.
 */
class PumpSettingDTO(var key: String, var value: String, var configurationGroup: PumpConfigurationGroup) {

    override fun toString(): String {
        return "PumpSettingDTO [key=" + key + ",value=" + value + ",group=" + configurationGroup.name + "]"
    }

}