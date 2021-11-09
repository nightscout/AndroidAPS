package info.nightscout.androidaps.plugins.pump.common.defs

import info.nightscout.androidaps.plugins.pump.common.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class PumpHistoryEntryGroup(val resourceId: Int) {

    All(R.string.history_group_all),
    Bolus(R.string.history_group_bolus),
    Basal(R.string.history_group_basal),
    Prime(R.string.history_group_prime),
    Configuration(R.string.history_group_configuration),
    Alarm(R.string.history_group_alarm),
    Glucose(R.string.history_group_glucose),
    Notification(R.string.history_group_notification),
    Statistic(R.string.history_group_statistic),
    Unknown(R.string.history_group_unknown);

    var translated: String? = null
        private set

    override fun toString(): String {
        return translated!!
    }

    companion object {

        private var translatedList: MutableList<PumpHistoryEntryGroup>? = null

        private fun doTranslation(rh: ResourceHelper) {
            translatedList = ArrayList()
            for (pumpHistoryEntryGroup in values()) {
                pumpHistoryEntryGroup.translated = rh.gs(pumpHistoryEntryGroup.resourceId)
                (translatedList as ArrayList<PumpHistoryEntryGroup>).add(pumpHistoryEntryGroup)
            }
        }

        fun getTranslatedList(rh: ResourceHelper): List<PumpHistoryEntryGroup> {
            if (translatedList == null) doTranslation(rh)
            return translatedList!!
        }
    }

}