package app.aaps.ui.compose.alertKinds

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.withClick
import app.aaps.ui.keys.UiIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides

/** Android has alerts (`AlertsAndroidBindings`), so it has the list of alert kinds, and "display over other apps". */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertKindsAndroidBindings {

    @Provides
    fun alertKindsSettings(impl: AlertKindsSettingsImpl, overOtherApps: AlarmOverOtherAppsSetting): AlertKindsSettings =
        object : AlertKindsSettings by impl {
            override val platformItems: List<PreferenceItem> = listOf(overOtherApps.item)
        }
}

/**
 * Opens Android's "Display over other apps" page for AAPS. Android does not let an app in the background put a screen
 * over another app, so without it a ringing alarm shows its notification, and its alarm screen only when AAPS is
 * opened (#5338). With it, `AndroidAlertPlatform` starts the alarm screen at once. Optional: AAPS never asks for it.
 */
@Inject
class AlarmOverOtherAppsSetting(private val context: Context) {

    val item: PreferenceItem = UiIntentKey.AlarmOverOtherApps.withClick { open() }

    private fun open() {
        val page = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))
        try {
            context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // A phone with no such page: the app's own page has the same switch on most of them
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
