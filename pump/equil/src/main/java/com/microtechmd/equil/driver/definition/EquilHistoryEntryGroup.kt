package com.microtechmd.equil.driver.definition

import app.aaps.core.interfaces.resources.ResourceHelper
import com.microtechmd.equil.R
import info.nightscout.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.pump.common.defs.PumpTypeGroupConfig
import kotlin.streams.toList

enum class EquilHistoryEntryGroup(val resourceId: Int, val pumpTypeGroupConfig: PumpTypeGroupConfig = PumpTypeGroupConfig.All) {

    All(R.string.equil_history_group_all),
    Pair(R.string.equil_history_group_pair),
    Bolus(R.string.equil_history_group_bolus),
    Basal(R.string.equil_history_group_basal),
    Prime(R.string.equil_history_group_prime),
    Configuration(R.string.equil_history_group_configuration),
    ;

    var translated: String? = null
        private set

    override fun toString(): String {
        return translated!!
    }

    companion object {

        @JvmStatic private var translatedList: MutableList<EquilHistoryEntryGroup>? = null

        fun doTranslation(rh: ResourceHelper) {
            if (translatedList != null) return
            translatedList = ArrayList()
            for (pumpHistoryEntryGroup in EquilHistoryEntryGroup.values()) {
                pumpHistoryEntryGroup.translated = rh.gs(pumpHistoryEntryGroup.resourceId)
                (translatedList as ArrayList<EquilHistoryEntryGroup>).add(pumpHistoryEntryGroup)
            }
        }

        // FIXME this is just for Java compatibility reasons (can be removed when all drivers using it are in Kotlin - OmnipodEros still in java)
        fun getTranslatedList(rh: ResourceHelper): List<EquilHistoryEntryGroup> {
            return getTranslatedList(rh, PumpTypeGroupConfig.All)
        }

        fun getTranslatedList(rh: ResourceHelper, pumpTypeGroupConfig: PumpTypeGroupConfig = PumpTypeGroupConfig.All): List<EquilHistoryEntryGroup> {
            if (translatedList == null) doTranslation(rh)

            var outList: List<EquilHistoryEntryGroup>

            // if (pumpTypeGroupConfig == PumpTypeGroupConfig.All) {
            //     outList = translatedList!!.stream()
            //         .filter { pre -> pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All }
            //         .toList();
            // } else {
            //     outList = translatedList!!.stream()
            //         .filter { pre -> (pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All || pre.pumpTypeGroupConfig == pumpTypeGroupConfig) }
            //         .toList();
            // }

            if (pumpTypeGroupConfig == PumpTypeGroupConfig.All) {
                outList = EquilHistoryEntryGroup.translatedList!!.filter { pre -> pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All }
            } else {
                outList = EquilHistoryEntryGroup.translatedList!!.filter { pre -> (pre.pumpTypeGroupConfig == PumpTypeGroupConfig.All || pre.pumpTypeGroupConfig == pumpTypeGroupConfig) }
            }

            return outList
        }
    }
}

