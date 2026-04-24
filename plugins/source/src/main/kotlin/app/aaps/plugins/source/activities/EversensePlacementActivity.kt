package app.aaps.plugins.source.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.aaps.plugins.source.R
import com.nightscout.eversense.EversenseCGMPlugin
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState

class EversensePlacementActivity : AppCompatActivity(), EversenseWatcher {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val eversense get() = EversenseCGMPlugin.instance

    private lateinit var bar1: ImageView
    private lateinit var bar2: ImageView
    private lateinit var bar3: ImageView
    private lateinit var bar4: ImageView
    private lateinit var bar5: ImageView
    private lateinit var signalLabel: TextView
    private lateinit var signalValue: TextView
    private lateinit var instructionText: TextView
    private lateinit var lastUpdateText: TextView

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (eversense.isConnected()) {
                Thread { eversense.readSignalStrength() }.start()
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eversense_placement)

        supportActionBar?.apply {
            title = getString(R.string.eversense_placement_title)
            setDisplayHomeAsUpEnabled(true)
        }

        bar1 = findViewById(R.id.signal_bar_1)
        bar2 = findViewById(R.id.signal_bar_2)
        bar3 = findViewById(R.id.signal_bar_3)
        bar4 = findViewById(R.id.signal_bar_4)
        bar5 = findViewById(R.id.signal_bar_5)
        signalLabel = findViewById(R.id.signal_label)
        signalValue = findViewById(R.id.rssi_value)
        instructionText = findViewById(R.id.instruction_text)
        lastUpdateText = findViewById(R.id.last_update_text)

        eversense.addWatcher(this)

        val state = eversense.getCurrentState()
        if (state != null && state.sensorSignalStrength > 0) {
            updateSignalUI(state.sensorSignalStrength)
        } else if (!eversense.isConnected()) {
            showNotConnected()
        } else {
            showWaiting()
        }

        mainHandler.post(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(pollRunnable)
        eversense.removeWatcher(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onStateChanged(state: EversenseState) {
        mainHandler.post {
            if (state.sensorSignalStrength > 0) updateSignalUI(state.sensorSignalStrength)
            else showWaiting()
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        mainHandler.post { if (!connected) showNotConnected() }
    }

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}

    private fun updateSignalUI(strength: Int) {
        val bars = strengthToBars(strength)
        val activeColor = when {
            bars >= 5 -> ContextCompat.getColor(this, R.color.signal_excellent)
            bars >= 4 -> ContextCompat.getColor(this, R.color.signal_good)
            bars >= 3 -> ContextCompat.getColor(this, R.color.signal_fair)
            else      -> ContextCompat.getColor(this, R.color.signal_poor)
        }
        val inactiveColor = ContextCompat.getColor(this, R.color.signal_inactive)
        listOf(bar1, bar2, bar3, bar4, bar5).forEachIndexed { index, bar ->
            bar.setColorFilter(if (index < bars) activeColor else inactiveColor)
        }
        val (label, instruction) = when {
            strength >= 75 -> Pair(getString(R.string.eversense_signal_excellent), getString(R.string.eversense_placement_instruction_excellent))
            strength >= 48 -> Pair(getString(R.string.eversense_signal_good), getString(R.string.eversense_placement_instruction_good))
            strength >= 30 -> Pair(getString(R.string.eversense_signal_fair), getString(R.string.eversense_placement_instruction_fair))
            strength >= 28 -> Pair(getString(R.string.eversense_signal_weak), getString(R.string.eversense_placement_instruction_weak))
            strength >= 25 -> Pair(getString(R.string.eversense_signal_poor), getString(R.string.eversense_placement_instruction_poor))
            else           -> Pair(getString(R.string.eversense_not_connected), getString(R.string.eversense_placement_instruction_not_connected))
        }
        signalLabel.text = label
        signalValue.text = "$strength%"
        instructionText.text = instruction
    }

    private fun showNotConnected() {
        val inactiveColor = ContextCompat.getColor(this, R.color.signal_inactive)
        listOf(bar1, bar2, bar3, bar4, bar5).forEach { it.setColorFilter(inactiveColor) }
        signalLabel.text = getString(R.string.eversense_not_connected)
        signalValue.text = ""
        instructionText.text = getString(R.string.eversense_placement_instruction_not_connected)
    }

    private fun showWaiting() {
        val inactiveColor = ContextCompat.getColor(this, R.color.signal_inactive)
        listOf(bar1, bar2, bar3, bar4, bar5).forEach { it.setColorFilter(inactiveColor) }
        signalLabel.text = getString(R.string.eversense_placement_reading)
        signalValue.text = ""
        instructionText.text = ""
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
