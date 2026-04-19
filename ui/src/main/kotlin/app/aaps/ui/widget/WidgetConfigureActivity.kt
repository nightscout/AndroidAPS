package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

class WidgetConfigureActivity : DaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to CANCELED so backing out of the activity cancels widget placement.
        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initialOpacity = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        val initialUseBlack = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)

        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    WidgetConfigureScreen(
                        initialOpacity = initialOpacity,
                        initialUseBlack = initialUseBlack,
                        onOpacityChange = { value ->
                            preferences.put(IntComposedKey.WidgetOpacity, appWidgetId, value = value)
                            Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
                        },
                        onUseBlackChange = { value ->
                            preferences.put(BooleanComposedKey.WidgetUseBlack, appWidgetId, value = value)
                            Widget.updateWidget(this@WidgetConfigureActivity, "WidgetConfigure")
                        },
                        onClose = {
                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
