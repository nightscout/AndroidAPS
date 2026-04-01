package app.aaps.plugins.source.activities

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.source.R
import com.nightscout.eversense.EversenseCGMPlugin
import com.nightscout.eversense.enums.CalibrationReadiness
import javax.inject.Inject

class EversenseCalibrationActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var profileUtil: ProfileUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eversense_calibration)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.eversense_calibration_action)

        val state = EversenseCGMPlugin.instance.getCurrentState()

        val statusText = findViewById<TextView>(R.id.calibration_status)
        statusText.text = when {
            state == null -> getString(R.string.eversense_not_connected)
            state.calibrationReadiness == CalibrationReadiness.READY -> getString(R.string.eversense_calibration_ready)
            else -> state.calibrationReadiness.name
        }

        val unitLabel = findViewById<TextView>(R.id.calibration_unit_label)
        unitLabel.text = profileUtil.units.asText

        val bgInput = findViewById<EditText>(R.id.calibration_bg_input)
        bgInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        bgInput.isEnabled = state?.calibrationReadiness == CalibrationReadiness.READY

        val submitButton = findViewById<Button>(R.id.calibration_submit_button)
        submitButton.isEnabled = state?.calibrationReadiness == CalibrationReadiness.READY
        submitButton.setOnClickListener {
            val bgValue = bgInput.text.toString().toDoubleOrNull()
            if (bgValue == null || bgValue <= 0) {
                Toast.makeText(this, getString(R.string.eversense_calibration_invalid_value), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bgMgDl = if (profileUtil.units.asText == "mmol") {
                (bgValue * 18.0182).toInt()
            } else {
                bgValue.toInt()
            }

            submitButton.isEnabled = false
            statusText.text = getString(R.string.eversense_calibration_sending)

            Thread {
                val success = EversenseCGMPlugin.instance.sendCalibration(bgMgDl)
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, getString(R.string.eversense_calibration_success), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        submitButton.isEnabled = true
                        statusText.text = getString(R.string.eversense_calibration_ready)
                        Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
