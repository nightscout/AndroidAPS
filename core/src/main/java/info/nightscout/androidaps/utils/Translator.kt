package info.nightscout.androidaps.utils

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 15.07.2016.
 */
@Singleton
class Translator @Inject internal constructor(
    private val resourceHelper: ResourceHelper
) {

    fun translate(text: String): String =
        when (text) {
            "BG Check"                -> resourceHelper.gs(R.string.careportal_bgcheck)
            "Snack Bolus"             -> resourceHelper.gs(R.string.careportal_snackbolus)
            "Meal Bolus"              -> resourceHelper.gs(R.string.careportal_mealbolus)
            "Correction Bolus"        -> resourceHelper.gs(R.string.careportal_correctionbolus)
            "Carb Correction"         -> resourceHelper.gs(R.string.careportal_carbscorrection)
            "Combo Bolus"             -> resourceHelper.gs(R.string.careportal_combobolus)
            "Announcement"            -> resourceHelper.gs(R.string.careportal_announcement)
            "Note"                    -> resourceHelper.gs(R.string.careportal_note)
            "Question"                -> resourceHelper.gs(R.string.careportal_question)
            "Exercise"                -> resourceHelper.gs(R.string.careportal_exercise)
            "Site Change"             -> resourceHelper.gs(R.string.careportal_pumpsitechange)
            "Pump Battery Change"     -> resourceHelper.gs(R.string.careportal_pumpbatterychange)
            "Sensor Start"            -> resourceHelper.gs(R.string.careportal_cgmsensorstart)
            "Sensor Change"           -> resourceHelper.gs(R.string.careportal_cgmsensorinsert)
            "Insulin Change"          -> resourceHelper.gs(R.string.careportal_insulincartridgechange)
            "Temp Basal Start"        -> resourceHelper.gs(R.string.careportal_tempbasalstart)
            "Temp Basal End"          -> resourceHelper.gs(R.string.careportal_tempbasalend)
            "Profile Switch"          -> resourceHelper.gs(R.string.careportal_profileswitch)
            "Temporary Target"        -> resourceHelper.gs(R.string.careportal_temporarytarget)
            "Temporary Target Cancel" -> resourceHelper.gs(R.string.careportal_temporarytargetcancel)
            "OpenAPS Offline"         -> resourceHelper.gs(R.string.careportal_openapsoffline)
            "Finger"                  -> resourceHelper.gs(R.string.glucosetype_finger)
            "Sensor"                  -> resourceHelper.gs(R.string.glucosetype_sensor)
            "Manual"                  -> resourceHelper.gs(R.string.manual)
            else                      -> resourceHelper.gs(R.string.unknown)
        }
}