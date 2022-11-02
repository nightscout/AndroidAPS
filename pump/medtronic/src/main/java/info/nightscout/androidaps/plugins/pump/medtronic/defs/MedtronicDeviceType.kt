package info.nightscout.androidaps.plugins.pump.medtronic.defs

import java.util.*

/**
 * Taken from GNU Gluco Control diabetes management software (ggc.sourceforge.net)
 *
 *
 * Author: Andy {andy@atech-software.com}
 */
enum class MedtronicDeviceType {

    Unknown_Device,  //

    // Pump
    Medtronic_511("511"),  //
    Medtronic_512("512"),  //
    Medtronic_712("712"),  //
    Medtronic_512_712(listOf(Medtronic_512, Medtronic_712)),  //
    Medtronic_515("515"),  //
    Medtronic_715("715"),  //
    Medtronic_515_715(listOf(Medtronic_515, Medtronic_715)),  //
    Medtronic_522("522"),  //
    Medtronic_722("722"),  //
    Medtronic_522_722(listOf(Medtronic_522, Medtronic_722)),  //
    Medtronic_523_Revel("523"),  //
    Medtronic_723_Revel("723"),  //
    Medtronic_554_Veo("554"),  //
    Medtronic_754_Veo("754"),  //
    Medtronic_512andHigher(listOf(Medtronic_512, Medtronic_712, Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo)),  //
    Medtronic_515andHigher(listOf(Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo)),  //
    Medtronic_522andHigher(listOf(Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo)),  //
    Medtronic_523andHigher(listOf(Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo)),  //
    Medtronic_554andHigher(listOf(Medtronic_554_Veo, Medtronic_754_Veo)),  //

    //
    All;

    companion object {
        var mapByDescription: MutableMap<String, MedtronicDeviceType> = mutableMapOf()

        fun isSameDevice(deviceWeCheck: MedtronicDeviceType, deviceSources: MedtronicDeviceType): Boolean {
            if (deviceSources.isFamily) {
                for (mdt in deviceSources.familyMembers!!) {
                    if (mdt == deviceWeCheck) return true
                }
            } else {
                return deviceWeCheck == deviceSources
            }
            return false
        }

        fun getByDescription(desc: String): MedtronicDeviceType {
            return if (mapByDescription.containsKey(desc)) {
                mapByDescription[desc]!!
            } else {
                Unknown_Device
            }
        }

        init {
            for (minimedDeviceType in values()) {
                if (!minimedDeviceType.isFamily && minimedDeviceType.pumpModel!=null) {
                    mapByDescription[minimedDeviceType.pumpModel!!] = minimedDeviceType
                }
            }
        }
    }

    var pumpModel: String? = null
        private set

    //    public static boolean isLargerFormat(MedtronicDeviceType model) {
    //        return isSameDevice(model, Medtronic_523andHigher);
    //    }
    val isFamily: Boolean
    var familyMembers: List<MedtronicDeviceType>? = null
        private set

    constructor() {
        isFamily = false
    }

    constructor(pumpModel: String?) {
        isFamily = false
        this.pumpModel = pumpModel
    }

    constructor(familyMembers: List<MedtronicDeviceType>) {
        this.familyMembers = familyMembers
        isFamily = true
    }

    val isMedtronic_523orHigher: Boolean
        get() = isSameDevice(this, Medtronic_523andHigher)

    val bolusStrokes: Int
        get() = if (isMedtronic_523orHigher) 40 else 10

}