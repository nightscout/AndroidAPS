package app.aaps.plugins.source.activities

import app.aaps.core.keys.interfaces.Preferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.source.R
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runInterruptible
import app.aaps.plugins.source.eversense.PositioningSession
import java.text.DateFormat
import java.util.Date
import dev.zacsweers.metro.Inject

class EversensePlacementActivity : MetroAppCompatActivity(), EversenseWatcher {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var eversense: EversenseCGMPlugin

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var signalLabel by mutableStateOf("")
    private var signalValue by mutableStateOf("")
    private var instructionText by mutableStateOf("")
    private var lastUpdateText by mutableStateOf("")
    private var signalBars by mutableIntStateOf(0)

    private val timeFormatter: DateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    private var resumed = false
    private val positioning by lazy {
        PositioningSession(ioScope,
            enter = { runInterruptible { eversense.enterPositioningMode() } },
            read = { runInterruptible { if (eversense.isConnected()) eversense.readSignalStrength() } },
            exit = { runInterruptible { eversense.exitPositioningMode() } }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEversenseContent(R.string.eversense_placement_title, preferences) {
            EversensePlacementContent(signalLabel, signalValue, instructionText, lastUpdateText, signalBars)
        }

        val state = eversense.getCurrentState()
        if (state.sensorSignalStrength > 0) {
            updateSignalUI(state.sensorSignalStrength)
        } else if (!eversense.isConnected()) {
            showNotConnected()
        } else {
            showWaiting()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        eversense.addWatcher(this)
        positioning.resume()
    }

    override fun onPause() {
        resumed = false
        positioning.pause()
        eversense.removeWatcher(this)
        mainHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        eversense.removeWatcher(this)
    }

    override fun onStateChanged(state: EversenseState) {
        mainHandler.post {
            if (!resumed) return@post
            if (state.sensorSignalStrength > 0) {
                updateSignalUI(state.sensorSignalStrength)
                lastUpdateText = getString(R.string.eversense_placement_last_update, timeFormatter.format(Date()))
            } else {
                showWaiting()
            }
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        mainHandler.post { if (resumed && !connected) showNotConnected() }
    }

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}

    private fun updateSignalUI(strength: Int) {
        signalBars = strengthToBars(strength)
        val (label, instruction) = when {
            strength >= 75 -> Pair(getString(R.string.eversense_signal_excellent), getString(R.string.eversense_placement_instruction_excellent))
            strength >= 48 -> Pair(getString(R.string.eversense_signal_good), getString(R.string.eversense_placement_instruction_good))
            strength >= 30 -> Pair(getString(R.string.eversense_signal_fair), getString(R.string.eversense_placement_instruction_fair))
            strength >= 28 -> Pair(getString(R.string.eversense_signal_weak), getString(R.string.eversense_placement_instruction_weak))
            strength >= 25 -> Pair(getString(R.string.eversense_signal_poor), getString(R.string.eversense_placement_instruction_poor))
            else           -> Pair(getString(R.string.eversense_not_connected), getString(R.string.eversense_placement_instruction_not_connected))
        }
        signalLabel = label
        signalValue = "$strength%"
        instructionText = instruction
    }

    private fun showNotConnected() {
        signalBars = 0
        signalLabel = getString(R.string.eversense_not_connected)
        signalValue = ""
        lastUpdateText = ""
        instructionText = getString(R.string.eversense_placement_instruction_not_connected)
    }

    private fun showWaiting() {
        signalBars = 0
        signalLabel = getString(R.string.eversense_placement_reading)
        signalValue = ""
        instructionText = ""
    }

    private fun strengthToBars(strength: Int): Int = when {
        strength >= 75 -> 5
        strength >= 48 -> 4
        strength >= 30 -> 3
        strength >= 28 -> 2
        strength >= 25 -> 1
        else           -> 0
    }
}
