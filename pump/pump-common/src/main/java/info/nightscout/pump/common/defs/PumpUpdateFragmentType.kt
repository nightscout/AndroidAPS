package info.nightscout.pump.common.defs

import java.util.Arrays

enum class PumpUpdateFragmentType {
    None,
    PumpStatus,
    DriverStatus,
    Queue,
    Bolus,
    TBR,
    ProfileChange,
    TBRCount,
    BolusCount,
    TreatmentValues(Arrays.asList(Bolus, TBR, TBRCount, BolusCount, ProfileChange)), // Last Bolus, TBR, Profile Change, TBR Count, Bolus Count
    Full,
    Configuration,  // Firmware, Errors
    Battery,
    Reservoir,
    OtherValues(Arrays.asList(Battery, Reservoir)), // Battery, Reservoir
    Custom_1,
    Custom_2,
    Custom_3,
    Custom_4,
    Custom_5,
    Custom_6,
    Custom_7,
    Custom_8
    ;

    var children: List<PumpUpdateFragmentType>? = null

    constructor()

    constructor(children: List<PumpUpdateFragmentType>) {
        this.children = children
    }

    fun isOptionIncluded(type: PumpUpdateFragmentType): Boolean {
        if (this == type)
            return true
        else if (this.children != null && this.children!!.contains(type))
            return true

        return false
    }

}