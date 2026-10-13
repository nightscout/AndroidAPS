package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.databinding.WidgetConfigureBinding
import dagger.android.DaggerActivity
import javax.inject.Inject

/**
 * The configuration screen for the [Widget] AppWidget.
 */
class WidgetConfigureActivity : DaggerActivity() {

    @Inject lateinit var preferences: Preferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: WidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Write the prefix to the SharedPreferences object for this widget
                preferences.put(IntComposedKey.WidgetOpacity, appWidgetId, value = progress)
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
            preferences.put(BooleanComposedKey.WidgetUseBlack, appWidgetId, value = value)
            Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
        }

        // Find the widget id from the intent.
        appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.seekBar.progress = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        binding.useBlack.isChecked = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.seekBar.setOnSeekBarChangeListener(null)
        binding.closeLayout.close.setOnClickListener(null)
        binding.useBlack.setOnCheckedChangeListener(null)
    }
}