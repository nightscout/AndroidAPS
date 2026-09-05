package app.aaps.wear.interaction.menus

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionResendData
import app.aaps.core.keys.BooleanKey
import app.aaps.wear.BuildConfig
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.interaction.activities.BgGraphActivity
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.watchfaces.WatchFacePushHelper
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

class MainMenuActivity : MenuListActivity() {

    @Inject lateinit var watchFacePushHelper: WatchFacePushHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.app_name)
        super.onCreate(savedInstanceState)
        rxBus.send(EventWearToMobile(ActionResendData("MainMenuListActivity")))
    }

    override fun onResume() {
        super.onResume()
        // The install entry only shows when the face is missing; verify against the live slot
        // state on every visit (the SP flag goes stale when the user uninstalls the face from
        // the watch settings)
        if (watchFacePushHelper.isSupported()) {
            lifecycleScope.launch {
                watchFacePushHelper.isFaceInstalled()
                refreshElements()
            }
        }
    }

    override fun provideTitleIcon(): Int = when (BuildConfig.FLAVOR) {
        "aapsclient", "aapsclient2", "aapsclient3" -> R.drawable.ic_aaps_client_light
        else                                        -> R.drawable.ic_aaps_light
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            if (!preferences.get(BooleanKey.WearControl)) {
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_sync, getString(R.string.menu_resync)))
            } else {
                add(MenuItem(R.drawable.ic_loop_closed, getString(R.string.loop_status)))
                add(MenuItem(R.drawable.ic_bg_graph, getString(R.string.menu_bg_graph)))
                if (sp.getBoolean(R.string.key_show_wizard, true))
                    add(MenuItem(R.drawable.ic_calculator, getString(R.string.menu_wizard)))
                add(MenuItem(R.drawable.ic_carbs_orange, getString(R.string.menu_ecarb)))
                add(MenuItem(R.drawable.ic_bolus_carbs, getString(R.string.menu_treatment)))
                add(MenuItem(R.drawable.ic_temptarget_flat, getString(R.string.loop_status_temp_target)))
                add(MenuItem(R.drawable.ic_profile_switch, getString(R.string.status_profile_switch)))
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_status, getString(R.string.menu_status)))
                if (sp.getBoolean(R.string.key_prime_fill, false))
                    add(MenuItem(R.drawable.ic_canula, getString(R.string.menu_prime_fill)))
            }
            // Bottom of the menu: only present on Wear OS 6+ (where CWF and the other code-based
            // faces cannot run) and only while the pushed face is not installed. Since each app
            // install/update pushes the face automatically, this is the recovery action for a
            // user who removed the face and wants it back before the next app update. While an
            // install runs the label switches to an inert "Installing…" so the tap is visibly
            // acknowledged (install can take a few seconds and a silent wait provokes second taps)
            if (watchFacePushHelper.isSupported() && !sp.getBoolean(WatchFacePushHelper.KEY_FACE_INSTALLED, false))
                add(MenuItem(R.drawable.watchface_aapsv4, getString(if (installing) R.string.menu_installing_watchface else R.string.menu_install_watchface)))
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.loop_status)             -> startActivity(Intent(this, LoopStatusActivity::class.java))
            getString(R.string.menu_bg_graph)           -> startActivity(Intent(this, BgGraphActivity::class.java))
            getString(R.string.menu_wizard)             -> startActivity(Intent(this, WizardActivity::class.java))
            getString(R.string.menu_ecarb)              -> startActivity(Intent(this, ECarbActivity::class.java))
            getString(R.string.menu_treatment)          -> startActivity(Intent(this, TreatmentActivity::class.java))
            getString(R.string.loop_status_temp_target) -> startActivity(Intent(this, TempTargetActivity::class.java))
            getString(R.string.status_profile_switch)   -> rxBus.send(EventWearToMobile(EventData.ActionProfileSwitchSendInitialData(System.currentTimeMillis())))
            getString(R.string.menu_settings)           -> startActivity(Intent(this, PreferenceMenuActivity::class.java))
            getString(R.string.menu_status)             -> startActivity(Intent(this, StatusMenuActivity::class.java))
            getString(R.string.menu_prime_fill)         -> startActivity(Intent(this, FillMenuActivity::class.java))
            getString(R.string.menu_resync)             -> rxBus.send(EventWearToMobile(ActionResendData("Re-Sync")))
            getString(R.string.menu_install_watchface)  -> installWatchFace()
        }
    }

    private fun installWatchFace() {
        // Setting the installed face as active needs this runtime permission (the API's one-shot
        // permissionless activation is not reliable, e.g. already consumed) — ask first, then
        // install either way: without the grant the face still installs and is selectable in the
        // watch face picker
        if (ContextCompat.checkSelfPermission(this, SET_ACTIVE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(SET_ACTIVE_PERMISSION), REQUEST_SET_ACTIVE_PERMISSION)
            return
        }
        doInstallWatchFace()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_SET_ACTIVE_PERMISSION) doInstallWatchFace()
        else super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // Double-tapping the menu entry must not race two installs (the loser finds the slot
    // occupied and reports a bogus failure); doAction runs on the main thread, so a plain flag is enough
    private var installing = false

    private fun doInstallWatchFace() {
        if (installing) return
        installing = true
        refreshElements()
        lifecycleScope.launch {
            try {
                val success = watchFacePushHelper.installOrUpdate(activate = true)
                val message = when {
                    !success                           -> R.string.watchface_install_failed
                    watchFacePushHelper.isFaceActive() -> R.string.watchface_installed_active
                    else                               -> R.string.watchface_installed_select
                }
                Toast.makeText(this@MainMenuActivity, getString(message), Toast.LENGTH_SHORT).show()
            } finally {
                installing = false
                refreshElements()
            }
        }
    }

    companion object {

        private const val SET_ACTIVE_PERMISSION = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
        private const val REQUEST_SET_ACTIVE_PERMISSION = 1
    }
}
