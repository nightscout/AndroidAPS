package info.nightscout.configuration.maintenance.activities

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.configuration.R
import info.nightscout.configuration.databinding.ActivityLogsettingBinding
import info.nightscout.rx.interfaces.L
import info.nightscout.rx.interfaces.LogElement
import javax.inject.Inject

class LogSettingActivity : DaggerAppCompatActivity() {

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

    internal inner class LogViewHolder(element: LogElement) {

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
