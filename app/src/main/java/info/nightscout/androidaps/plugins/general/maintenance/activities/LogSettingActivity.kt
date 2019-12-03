package info.nightscout.androidaps.plugins.general.maintenance.activities

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.logging.L
import kotlinx.android.synthetic.main.activity_logsetting.*

class LogSettingActivity : NoSplashAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logsetting)

        createViewsForSettings()

        logsettings_reset.setOnClickListener {
            L.resetToDefaults()
            createViewsForSettings()
        }
    }

    private fun createViewsForSettings() {
        logsettings_placeholder.removeAllViews()
        for (element in L.getLogElements()) {
            val logViewHolder = LogViewHolder(element)
            logsettings_placeholder.addView(logViewHolder.baseView)
        }

    }

    internal inner class LogViewHolder(element: L.LogElement) {
        var baseView: LinearLayout = layoutInflater.inflate(R.layout.logsettings_item, null) as LinearLayout

        init {
            (baseView.findViewById<View>(R.id.logsettings_description) as TextView).text = element.name
            val enabled = baseView.findViewById<CheckBox>(R.id.logsettings_visibility)
            enabled.isChecked = element.enabled
            enabled.setOnClickListener { element.setEnabled(enabled.isChecked) }
        }

    }
}
