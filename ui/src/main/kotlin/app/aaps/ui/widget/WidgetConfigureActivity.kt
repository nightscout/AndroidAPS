package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.ui.databinding.WidgetConfigureBinding
import dagger.android.DaggerActivity
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

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        val binding = WidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Write the prefix to the SharedPreferences object for this widget
                sp.putInt(PREF_PREFIX_KEY + appWidgetId, progress)
                Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
            }
        })

        binding.closeLayout.close.setOnClickListener {
            // Make sure we pass back the original appWidgetId
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        binding.useBlack.setOnCheckedChangeListener { _, value ->
            sp.putBoolean(PREF_PREFIX_KEY + "use_black_$appWidgetId", value)
            Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
        }

        // Find the widget id from the intent.
        appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.seekBar.progress = sp.getInt(PREF_PREFIX_KEY + appWidgetId, 25)
        binding.useBlack.isChecked = sp.getBoolean(PREF_PREFIX_KEY + "use_black_$appWidgetId", false)
    }
}