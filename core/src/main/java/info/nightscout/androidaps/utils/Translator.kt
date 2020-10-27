package info.nightscout.androidaps.utils

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.db.CareportalEvent
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
            CareportalEvent.BGCHECK             -> resourceHelper.gs(R.string.careportal_bgcheck)
            CareportalEvent.SNACKBOLUS          -> resourceHelper.gs(R.string.careportal_snackbolus)
            CareportalEvent.MEALBOLUS           -> resourceHelper.gs(R.string.careportal_mealbolus)
            CareportalEvent.CORRECTIONBOLUS     -> resourceHelper.gs(R.string.careportal_correctionbolus)
            CareportalEvent.CARBCORRECTION      -> resourceHelper.gs(R.string.careportal_carbscorrection)
            CareportalEvent.COMBOBOLUS          -> resourceHelper.gs(R.string.careportal_combobolus)
            CareportalEvent.ANNOUNCEMENT        -> resourceHelper.gs(R.string.careportal_announcement)
            CareportalEvent.NOTE                -> resourceHelper.gs(R.string.careportal_note)
            CareportalEvent.QUESTION            -> resourceHelper.gs(R.string.careportal_question)
            CareportalEvent.EXERCISE            -> resourceHelper.gs(R.string.careportal_exercise)
            CareportalEvent.SITECHANGE          -> resourceHelper.gs(R.string.careportal_pumpsitechange)
            CareportalEvent.PUMPBATTERYCHANGE   -> resourceHelper.gs(R.string.careportal_pumpbatterychange)
            CareportalEvent.SENSORSTART         -> resourceHelper.gs(R.string.careportal_cgmsensorstart)
            CareportalEvent.SENSORCHANGE        -> resourceHelper.gs(R.string.careportal_cgmsensorinsert)
            CareportalEvent.INSULINCHANGE       -> resourceHelper.gs(R.string.careportal_insulincartridgechange)
            CareportalEvent.TEMPBASALSTART      -> resourceHelper.gs(R.string.careportal_tempbasalstart)
            CareportalEvent.TEMPBASALEND        -> resourceHelper.gs(R.string.careportal_tempbasalend)
            CareportalEvent.PROFILESWITCH       -> resourceHelper.gs(R.string.careportal_profileswitch)
            CareportalEvent.TEMPORARYTARGET     -> resourceHelper.gs(R.string.careportal_temporarytarget)
            CareportalEvent.TEMPBASALCANCEL     -> resourceHelper.gs(R.string.careportal_temporarytargetcancel)
            CareportalEvent.OPENAPSOFFLINE      -> resourceHelper.gs(R.string.careportal_openapsoffline)
            CareportalEvent.MBG                 -> resourceHelper.gs(R.string.careportal_mbg)
            CareportalEvent.FINGER              -> resourceHelper.gs(R.string.glucosetype_finger)
            CareportalEvent.SENSOR              -> resourceHelper.gs(R.string.glucosetype_sensor)
            CareportalEvent.MANUAL              -> resourceHelper.gs(R.string.manual)
            else                                -> resourceHelper.gs(R.string.unknown)
        }
}