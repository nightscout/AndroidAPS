package info.nightscout.configuration.maintenance.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuProvider
import info.nightscout.configuration.R
import info.nightscout.configuration.databinding.ActivityLogsettingBinding
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.rx.interfaces.L
import info.nightscout.rx.interfaces.LogElement
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class LogSettingActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var l: L
    @Inject lateinit var rh: ResourceHelper

    private lateinit var binding: ActivityLogsettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.nav_logsettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        createViewsForSettings()

        binding.reset.setOnClickListener {
            l.resetToDefaults()
            createViewsForSettings()
        }
        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
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
