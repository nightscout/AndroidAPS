package app.aaps.core.ui.compose

import android.text.format.DateFormat
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun SystemBarAppearance(isDark: Boolean) {
    val view = LocalView.current
    // LocalActivity rather than `view.context as Activity`: that cast is unchecked, and the view's
    // context is usually a ContextWrapper, so it would throw rather than return null. Skipping the
    // bar styling is the right fallback - there is no window to style.
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val controller = WindowInsetsControllerCompat(activity.window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}

@Composable
actual fun smallestScreenWidthDp(): Int = LocalConfiguration.current.smallestScreenWidthDp

@Composable
actual fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@Composable
actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
