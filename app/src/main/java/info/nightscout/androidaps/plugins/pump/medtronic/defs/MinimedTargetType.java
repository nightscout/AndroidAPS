package info.nightscout.androidaps.plugins.pump.medtronic.defs;

/**
 * Taken from GNU Gluco Control diabetes management software (ggc.sourceforge.net)
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum MinimedTargetType {
    ActionCommand, //
    InitCommand,

    PumpConfiguration, //
    PumpData, //
    PumpSetData, //
    PumpConfiguration_NA, //
    PumpData_NA, //

    PumpDataAndConfiguration, //
    CGMSConfiguration, //
    CGMSData, //

    BaseCommand, //

    Pump, //
    CGMS, //

    CGMSData_NA, //
    CGMSConfiguration_NA;

    MinimedTargetType() {
    }

}
