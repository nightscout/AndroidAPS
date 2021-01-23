package info.nightscout.androidaps.plugins.general.maintenance.activities

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.databinding.ActivityLogsettingBinding
import info.nightscout.androidaps.logging.L
import javax.inject.Inject

class LogSettingActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var l: L

    private lateinit var binding: ActivityLogsettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createViewsForSettings()

        binding.reset.setOnClickListener {
            l.resetToDefaults()
            createViewsForSettings()
        }
        binding.ok.setOnClickListener { finish() }
    }

    private fun createViewsForSettings() {
        binding.placeholder.removeAllViews()
        for (element in l.getLogElements()) {
            val logViewHolder = LogViewHolder(element)
            binding.placeholder.addView(logViewHolder.baseView)
        }

    }

    internal inner class LogViewHolder(element: L.LogElement) {

        @Suppress("InflateParams")
        var baseView = layoutInflater.inflate(R.layout.logsettings_item, null) as LinearLayout

        init {
            (baseView.findViewById<View>(R.id.logsettings_description) as TextView).text = element.name
            val enabled = baseView.findViewById<CheckBox>(R.id.logsettings_visibility)
            enabled.isChecked = element.enabled
            enabled.setOnClickListener { element.enable(enabled.isChecked) }
        }

    }
}
