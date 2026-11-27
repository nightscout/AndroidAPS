package app.aaps.ui.widget.glance

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The intent that opens the app when a widget is tapped, or null if the system gives none.
 *
 * `getLaunchIntentForPackage` asks the package manager: a binder call to the system, which can be slow when the system
 * is busy. The widgets asked it inside their composition, which runs on the main thread, at every redraw, and the app
 * did not answer (ANR, Crashlytics 2026-11). So `provideGlance` asks it once per widget session, here, off the main
 * thread, and passes the result to the composition.
 */
internal suspend fun widgetLaunchIntent(context: Context): Intent? =
    withContext(Dispatchers.IO) { context.packageManager.getLaunchIntentForPackage(context.packageName) }
