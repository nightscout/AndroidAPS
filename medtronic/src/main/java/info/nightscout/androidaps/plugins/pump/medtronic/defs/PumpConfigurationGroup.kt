package info.nightscout.androidaps.plugins.pump.medtronic.defs

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class PumpConfigurationGroup(var code: Int) {

    General(1),  //
    Device(2),  //
    Insulin(3),  //
    Basal(4),  //
    Bolus(5),  //
    Sound(6),  //
    Other(20),  //
    UnknownGroup(21);

}