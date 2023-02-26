package info.nightscout.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import dagger.android.DaggerActivity
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.ui.databinding.WidgetConfigureBinding
import javax.inject.Inject

/**
 * The configuration screen for the [Widget] AppWidget.
 */
class WidgetConfigureActivity : DaggerActivity() {

    @Inject lateinit var sp: SP

    companion object {

        const val PREF_PREFIX_KEY = "appwidget_"
        const val DEFAULT_OPACITY = 25
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var value = 0

    private lateinit var binding: WidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Make sure we pass back the original appWidgetId
                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                value = progress
                saveTitlePref(appWidgetId, value)
                Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
            }
        })

        // Find the widget id from the intent.
        appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.seekBar.progress = loadTitlePref(appWidgetId)
    }

    // Write the prefix to the SharedPreferences object for this widget
    fun saveTitlePref(appWidgetId: Int, value: Int) {
        sp.putInt(PREF_PREFIX_KEY + appWidgetId, value)
    }

    // Read the prefix from the SharedPreferences object for this widget.
    // If there is no preference saved, get the default from a resource
    private fun loadTitlePref(appWidgetId: Int): Int = sp.getInt(PREF_PREFIX_KEY + appWidgetId, 25)
}