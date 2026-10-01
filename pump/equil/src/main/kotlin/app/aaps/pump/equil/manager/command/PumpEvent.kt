package app.aaps.pump.equil.manager.command

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.equil.R

object PumpEvent {

    /** Resource-based lookup — use from Compose via stringResource() */
    private val eventResMap: Map<Triple<Int, Int, Int>, Int> = mapOf(
        Triple(4, 1, 1) to R.string.equil_history_item1,
        Triple(4, 1, 2) to R.string.equil_history_item2,
        Triple(4, 2, 2) to R.string.equil_history_item3,
        Triple(4, 3, 0) to R.string.equil_history_item4,
        Triple(4, 3, 2) to R.string.equil_history_item5,
        Triple(4, 5, 0) to R.string.equil_history_item6,
        Triple(4, 5, 1) to R.string.equil_history_item7,
        Triple(4, 6, 1) to R.string.equil_history_item8,
        Triple(4, 6, 2) to R.string.equil_history_item9,
        Triple(4, 7, 0) to R.string.equil_history_item10,
        Triple(4, 8, 0) to R.string.equil_history_item11,
        Triple(4, 9, 0) to R.string.equil_history_item12,
        Triple(4, 10, 0) to R.string.equil_history_item13,
        Triple(4, 11, 0) to R.string.equil_history_item14,
        Triple(5, 0, 1) to R.string.equil_history_item15,
        Triple(5, 0, 2) to R.string.equil_history_item16,
        Triple(5, 1, 0) to R.string.equil_history_item17,
        Triple(5, 1, 2) to R.string.equil_history_item18,
    )

    @StringRes
    fun getEventStringRes(port: Int, type: Int, level: Int): Int? =
        eventResMap[Triple(port, type, level)]

    /** Alarm error lookup — subset of events that trigger notifications */
    private val errorResMap: Map<Triple<Int, Int, Int>, Int> = mapOf(
        Triple(4, 2, 2) to R.string.equil_history_item3,
        Triple(4, 3, 0) to R.string.equil_history_item4,
        Triple(4, 3, 2) to R.string.equil_history_item5,
        Triple(4, 6, 1) to R.string.equil_shutdown_be,
        Triple(4, 6, 2) to R.string.equil_shutdown,
        Triple(4, 8, 0) to R.string.equil_shutdown,
        Triple(5, 1, 2) to R.string.equil_history_item18,
    )

    fun getErrorString(rh: ResourceHelper, port: Int, type: Int, level: Int): String =
        errorResMap[Triple(port, type, level)]?.let { rh.gs(it) } ?: ""
}
