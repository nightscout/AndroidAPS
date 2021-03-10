package info.nightscout.androidaps.utils

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Translator @Inject internal constructor(
    private val resourceHelper: ResourceHelper
) {

    fun translate(text: String): String =
        when (text) {
            TherapyEvent.Type.FINGER_STICK_BG_VALUE.text   -> resourceHelper.gs(R.string.careportal_bgcheck)
            TherapyEvent.Type.SNACK_BOLUS.text             -> resourceHelper.gs(R.string.careportal_snackbolus)
            TherapyEvent.Type.MEAL_BOLUS.text              -> resourceHelper.gs(R.string.careportal_mealbolus)
            TherapyEvent.Type.CORRECTION_BOLUS.text        -> resourceHelper.gs(R.string.careportal_correctionbolus)
            TherapyEvent.Type.CARBS_CORRECTION.text        -> resourceHelper.gs(R.string.careportal_carbscorrection)
            TherapyEvent.Type.COMBO_BOLUS.text             -> resourceHelper.gs(R.string.careportal_combobolus)
            TherapyEvent.Type.ANNOUNCEMENT.text            -> resourceHelper.gs(R.string.careportal_announcement)
            TherapyEvent.Type.NOTE.text                    -> resourceHelper.gs(R.string.careportal_note)
            TherapyEvent.Type.QUESTION.text                -> resourceHelper.gs(R.string.careportal_question)
            TherapyEvent.Type.EXERCISE.text                -> resourceHelper.gs(R.string.careportal_exercise)
            TherapyEvent.Type.CANNULA_CHANGE.text          -> resourceHelper.gs(R.string.careportal_pumpsitechange)
            TherapyEvent.Type.PUMP_BATTERY_CHANGE.text     -> resourceHelper.gs(R.string.careportal_pumpbatterychange)
            TherapyEvent.Type.SENSOR_STARTED.text          -> resourceHelper.gs(R.string.careportal_cgmsensorstart)
            TherapyEvent.Type.SENSOR_STOPPED.text          -> resourceHelper.gs(R.string.careportal_cgm_sensor_stop)
            TherapyEvent.Type.SENSOR_CHANGE.text           -> resourceHelper.gs(R.string.careportal_cgmsensorinsert)
            TherapyEvent.Type.INSULIN_CHANGE.text          -> resourceHelper.gs(R.string.careportal_insulincartridgechange)
            TherapyEvent.Type.DAD_ALERT.text               -> resourceHelper.gs(R.string.careportal_dad_alert)
            TherapyEvent.Type.TEMPORARY_BASAL_START.text   -> resourceHelper.gs(R.string.careportal_tempbasalstart)
            TherapyEvent.Type.TEMPORARY_BASAL_END.text     -> resourceHelper.gs(R.string.careportal_tempbasalend)
            TherapyEvent.Type.PROFILE_SWITCH.text          -> resourceHelper.gs(R.string.careportal_profileswitch)
            TherapyEvent.Type.TEMPORARY_TARGET.text        -> resourceHelper.gs(R.string.careportal_temporarytarget)
            TherapyEvent.Type.TEMPORARY_TARGET_CANCEL.text -> resourceHelper.gs(R.string.careportal_temporarytargetcancel)
            TherapyEvent.Type.APS_OFFLINE.text             -> resourceHelper.gs(R.string.careportal_openapsoffline)
            TherapyEvent.Type.NS_MBG.text                  -> resourceHelper.gs(R.string.careportal_mbg)
            TherapyEvent.MeterType.FINGER.text             -> resourceHelper.gs(R.string.glucosetype_finger)
            TherapyEvent.MeterType.SENSOR.text             -> resourceHelper.gs(R.string.glucosetype_sensor)
            TherapyEvent.MeterType.MANUAL.text             -> resourceHelper.gs(R.string.manual)
            else                                           -> resourceHelper.gs(R.string.unknown)
        }
}