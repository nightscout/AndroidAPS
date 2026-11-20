package app.aaps.pump.ap550s.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.ap550s.AP550Pump
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.database.model.ConnectHistoryItem
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One line of the connection status history: when something happened, who reported it and what it
 * was.
 */
@Immutable
data class ConnectStatusRow(
    val time: String,
    val source: String,
    val description: String
)

/**
 * Connection status history for the AP550 driver.
 *
 * The list lives in memory only ([AP550Pump.historyLink]) and is not persisted, so it starts empty
 * after every app restart. Newest entry first.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class Ap550ConnectStatusViewModel(
    private val rh: ResourceHelper,
    private val ap550Pump: AP550Pump,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus
) : ViewModel() {

    private val _rows = MutableStateFlow<List<ConnectStatusRow>>(emptyList())
    val rows: StateFlow<List<ConnectStatusRow>> = _rows

    private var refreshTimestamp = 0L
    private var refreshFlag = false

    init {
        load()
    }

    /** Re-reads the in-memory list. Called when the screen opens and by the refresh button. */
    fun refresh() {
        val now = System.currentTimeMillis()
        when {
            now - refreshTimestamp < REFRESH_THROTTLE_MS && !refreshFlag -> {
                refreshFlag = true
                rxBus.send(EventShowSnackbar(rh.gs(R.string.ap550_do_not_refresh_frequently), EventShowSnackbar.Type.Info))
            }

            now - refreshTimestamp > REFRESH_THROTTLE_MS              -> {
                refreshFlag = false
                rxBus.send(EventShowSnackbar(rh.gs(R.string.ap550_refreshed), EventShowSnackbar.Type.Info))
            }
        }
        refreshTimestamp = now
        load()
    }

    private fun load() {
        _rows.value = ap550Pump.historyLink
            .sortedWith(ConnectHistoryItem.Comparator())
            .map { item ->
                ConnectStatusRow(
                    time = item.dateTime?.toDateTime()?.let { dateUtil.dateAndTimeAndSecondsString(it.millis) } ?: "",
                    source = item.source?.desc ?: "",
                    description = item.getDescription(rh)
                )
            }
    }

    companion object {

        /** Two presses closer together than this are treated as a double tap. */
        private const val REFRESH_THROTTLE_MS = 2_000L
    }
}