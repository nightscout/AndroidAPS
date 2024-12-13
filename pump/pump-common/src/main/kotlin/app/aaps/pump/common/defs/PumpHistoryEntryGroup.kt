package app.aaps.pump.common.defs

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.R

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class PumpHistoryEntryGroup(val resourceId: Int, val pumpTypeGroupConfig: PumpTypeGroupConfig = PumpTypeGroupConfig.All) {

    All(R.string.history_group_all),
    Base(R.string.history_group_base),
    Bolus(R.string.history_group_bolus),
    Basal(R.string.history_group_basal),
    Prime(R.string.history_group_prime),
    Configuration(R.string.history_group_configuration),
    Alarm(R.string.history_group_alarm),
    Glucose(R.string.history_group_glucose),
    Notification(R.string.history_group_notification),
    Statistic(R.string.history_group_statistic),
    Other(R.string.history_group_other),
    Unknown(R.string.history_group_unknown),

    // Ypso
    EventsOnly(R.string.history_group_events),
    EventsNoStat(R.string.history_group_events_no_stat)

    ;

    var translated: String? = null
        private set

    override fun toString(): String {
        return translated!!
    }

    companion object {

        @JvmStatic private var translatedList: MutableList<PumpHistoryEntryGroup>? = null

        fun doTranslation(rh: ResourceHelper) {
            if (translatedList != null) return
            translatedList = ArrayList()
            for (pumpHistoryEntryGroup in PumpHistoryEntryGroup.entries) {
                pumpHistoryEntryGroup.translated = rh.gs(pumpHistoryEntryGroup.resourceId)
                (translatedList as ArrayList<PumpHistoryEntryGroup>).add(pumpHistoryEntryGroup)
            }
        }

        // FIXME this is just for Java compatibility reasons (can be removed when all drivers using it are in Kotlin - OmnipodEros still in java)
        fun getTranslatedList(rh: ResourceHelper): List<PumpHistoryEntryGroup> {
            return getTranslatedList(rh, PumpTypeGroupConfig.All)
        }

        fun getTranslatedList(rh: ResourceHelper, pumpTypeGroupConfig: PumpTypeGroupConfig = PumpTypeGroupConfig.All): List<PumpHistoryEntryGroup> {
            if (translatedList == null) doTranslation(rh)

            val outList: List<PumpHistoryEntryGroup> =
                if (pumpTypeGroupConfig == PumpTypeGroupConfig.All) {
                    translatedList!!.filter { pre -> pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All }
                } else {
                    translatedList!!.filter { pre -> (pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All || pre.pumpTypeGroupConfig == pumpTypeGroupConfig) }
                }

            return outList
        }
    }
}