package app.aaps.plugins.eversense.placement

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.enums.SignalStrength
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The transmitter placement guide: while it is on screen, the transmitter is in diagnostic mode and the signal from the
 * sensor is read every half second, so the user can move the transmitter until the signal is good. It leaves
 * diagnostic mode when the screen goes away or the app goes to the background.
 */
// Registered in EversenseRegistration. Unscoped, so each guide gets its own.
@Stable
@Inject
class EversensePlacementViewModel(
    private val eversense: EversenseDriver,
    private val dateUtil: DateUtil
) : ViewModel() {

    sealed interface UiState {

        /** No link to the transmitter: nothing to read. */
        data object NotConnected : UiState

        /** Connected, no reading yet. */
        data object Reading : UiState
        data class Signal(val percent: Int, val strength: SignalStrength, val readAt: Long) : UiState
    }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val positioning = PositioningSession(
        viewModelScope,
        enter = { eversense.enterPositioningMode() },
        read = { read() },
        exit = { eversense.exitPositioningMode() }
    )

    init {
        eversense.connectionState.onEach { state ->
            if (state != EversenseDriver.ConnectionState.READY) _uiState.value = UiState.NotConnected
            else if (_uiState.value == UiState.NotConnected) _uiState.value = UiState.Reading
        }.launchIn(viewModelScope)
    }

    private fun initialState(): UiState = when {
        !eversense.isConnected() -> UiState.NotConnected
        else                     -> UiState.Reading
    }

    private suspend fun read() {
        if (!eversense.isConnected()) return
        val percent = eversense.readSignalStrength() ?: return
        _uiState.value = UiState.Signal(percent, SignalStrength.from365(percent), dateUtil.now())
    }

    /** The guide is on screen: enter diagnostic mode and read. */
    fun resume() = positioning.resume()

    /** The guide is hidden: stop reading and leave diagnostic mode. */
    fun pause() = positioning.pause()

    fun timeString(millis: Long): String = dateUtil.timeStringWithSeconds(millis)
}
