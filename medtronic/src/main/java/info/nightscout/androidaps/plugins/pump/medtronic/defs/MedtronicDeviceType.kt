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
    Medtronic_512_712(Medtronic_512, Medtronic_712),  //
    Medtronic_515("515"),  //
    Medtronic_715("715"),  //
    Medtronic_515_715(Medtronic_515, Medtronic_715),  //
    Medtronic_522("522"),  //
    Medtronic_722("722"),  //
    Medtronic_522_722(Medtronic_522, Medtronic_722),  //
    Medtronic_523_Revel("523"),  //
    Medtronic_723_Revel("723"),  //
    Medtronic_554_Veo("554"),  //
    Medtronic_754_Veo("754"),  //
    Medtronic_512andHigher(Medtronic_512, Medtronic_712, Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo),  //
    Medtronic_515andHigher(Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo),  //
    Medtronic_522andHigher(Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo),  //
    Medtronic_523andHigher(Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo),  //
    Medtronic_554andHigher(Medtronic_554_Veo, Medtronic_754_Veo),  //

    //
    All;

    companion object {
        var mapByDescription: MutableMap<String, MedtronicDeviceType>? = null

        @JvmStatic
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
            return if (mapByDescription==null) {
                Unknown_Device
            } else if (mapByDescription!!.containsKey(desc)) {
                mapByDescription!![desc]!!
            } else {
                Unknown_Device
            }
        }

        init {
            mapByDescription = HashMap()
            for (minimedDeviceType in values()) {
                if (!minimedDeviceType.isFamily) {
                    mapByDescription!![minimedDeviceType.pumpModel!!] = minimedDeviceType
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
    var familyMembers: Array<MedtronicDeviceType>? = null
        private set

    constructor(pumpModel: String?) {
        isFamily = false
        this.pumpModel = pumpModel
    }

    constructor(vararg familyMembers: MedtronicDeviceType) {
        this.familyMembers = familyMembers as Array<MedtronicDeviceType>?
        isFamily = true
    }

    val isMedtronic_523orHigher: Boolean
        get() = isSameDevice(this, Medtronic_523andHigher)

    val bolusStrokes: Int
        get() = if (isMedtronic_523orHigher) 40 else 10

}