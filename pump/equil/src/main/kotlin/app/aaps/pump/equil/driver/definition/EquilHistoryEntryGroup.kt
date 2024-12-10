package app.aaps.pump.equil.driver.definition

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.equil.R

enum class EquilHistoryEntryGroup(val resourceId: Int) {

    All(R.string.equil_history_group_all),
    Pair(R.string.equil_history_group_pair),
    Bolus(R.string.equil_history_group_bolus),
    Basal(R.string.equil_history_group_basal),
    Configuration(R.string.equil_history_group_configuration),
    ;

    var translated: String? = null
        private set

    override fun toString(): String {
        return translated!!
    }

    companion object {

        private var translatedList: MutableList<EquilHistoryEntryGroup> = mutableListOf()

        fun getTranslatedList(rh: ResourceHelper): List<EquilHistoryEntryGroup> {
            if (translatedList.isEmpty()) {
                for (pumpHistoryEntryGroup in entries) {
                    pumpHistoryEntryGroup.translated = rh.gs(pumpHistoryEntryGroup.resourceId)
                    translatedList.add(pumpHistoryEntryGroup)
                }
            }
            return translatedList
        }
    }
}

