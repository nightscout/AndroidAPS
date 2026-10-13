package app.aaps.pump.equil.manager.command

import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.equil.R
import java.util.Objects

class PumpEvent(@get:VisibleForTesting var port: Int, @get:VisibleForTesting var type: Int, @get:VisibleForTesting var level: Int, var comment: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val pumpEvent = other as PumpEvent
        return port == pumpEvent.port && type == pumpEvent.type && level == pumpEvent.level
    }

    override fun hashCode(): Int {
        return Objects.hash(port, type, level)
    }

    companion object {

        var lists: MutableList<PumpEvent> = ArrayList<PumpEvent>()

        fun init(rh: ResourceHelper) {
            lists = ArrayList<PumpEvent>()
            lists.add(PumpEvent(4, 0, 0, "--"))
            lists.add(PumpEvent(4, 1, 1, rh.gs(R.string.equil_history_item1)))
            lists.add(PumpEvent(4, 1, 2, rh.gs(R.string.equil_history_item2)))
            lists.add(PumpEvent(4, 2, 2, rh.gs(R.string.equil_history_item3)))
            lists.add(PumpEvent(4, 3, 0, rh.gs(R.string.equil_history_item4)))
            lists.add(PumpEvent(4, 3, 2, rh.gs(R.string.equil_history_item5)))
            lists.add(PumpEvent(4, 5, 0, rh.gs(R.string.equil_history_item6)))
            lists.add(PumpEvent(4, 5, 1, rh.gs(R.string.equil_history_item7)))
            lists.add(PumpEvent(4, 6, 1, rh.gs(R.string.equil_history_item8)))
            lists.add(PumpEvent(4, 6, 2, rh.gs(R.string.equil_history_item9)))
            lists.add(PumpEvent(4, 7, 0, rh.gs(R.string.equil_history_item10)))
            lists.add(PumpEvent(4, 8, 0, rh.gs(R.string.equil_history_item11)))
            lists.add(PumpEvent(4, 9, 0, rh.gs(R.string.equil_history_item12)))
            lists.add(PumpEvent(4, 10, 0, rh.gs(R.string.equil_history_item13)))
            lists.add(PumpEvent(4, 11, 0, rh.gs(R.string.equil_history_item14)))
            lists.add(PumpEvent(5, 0, 1, rh.gs(R.string.equil_history_item15)))
            lists.add(PumpEvent(5, 0, 2, rh.gs(R.string.equil_history_item16)))
            lists.add(PumpEvent(5, 1, 0, rh.gs(R.string.equil_history_item17)))
            lists.add(PumpEvent(5, 1, 2, rh.gs(R.string.equil_history_item18)))
        }

        fun getTips(port: Int, type: Int, level: Int): String {
            val pumpEvent = PumpEvent(port, type, level, "")
            val index = lists.indexOf(pumpEvent)
            if (index == -1) {
                return ""
            }
            return lists[index].comment
        }
    }
}
