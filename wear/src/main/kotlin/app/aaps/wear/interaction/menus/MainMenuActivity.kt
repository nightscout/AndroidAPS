package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
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
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.utils.MenuListActivity

class MainMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.FLAVOR == "full") {
            setTitle(R.string.app_name)
        } else if (BuildConfig.FLAVOR == "aapsclient") {
            setTitle("AAPSClient")
        } else if (BuildConfig.FLAVOR == "pumpcontrol"){
            setTitle("Pumpcontrol")
        }
        super.onCreate(savedInstanceState)
        rxBus.send(EventWearToMobile(ActionResendData("MainMenuListActivity")))
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            if (!preferences.get(BooleanKey.WearControl)) {
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_sync, getString(R.string.menu_resync)))
            } else {
                add(MenuItem(R.drawable.ic_loop_closed, getString(R.string.loop_status)))
                if (sp.getBoolean(R.string.key_show_wizard, true))
                    add(MenuItem(R.drawable.ic_calculator, getString(R.string.menu_wizard)))
                add(MenuItem(R.drawable.ic_e_carbs, getString(R.string.menu_ecarb)))
                add(MenuItem(R.drawable.ic_treatment, getString(R.string.menu_treatment)))
                add(MenuItem(R.drawable.ic_temptarget, getString(R.string.menu_tempt)))
                add(MenuItem(R.drawable.ic_profile, getString(R.string.status_profile_switch)))
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_status, getString(R.string.menu_status)))
                if (sp.getBoolean(R.string.key_prime_fill, false))
                    add(MenuItem(R.drawable.ic_canula, getString(R.string.menu_prime_fill)))
            }
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.menu_settings)         -> startActivity(Intent(this, PreferenceMenuActivity::class.java))
            getString(R.string.menu_resync)           -> rxBus.send(EventWearToMobile(ActionResendData("Re-Sync")))
            getString(R.string.status_profile_switch) -> rxBus.send(EventWearToMobile(EventData.ActionProfileSwitchSendInitialData(System.currentTimeMillis())))
            getString(R.string.menu_tempt)            -> startActivity(Intent(this, TempTargetActivity::class.java))
            getString(R.string.menu_treatment)        -> startActivity(Intent(this, TreatmentActivity::class.java))
            getString(R.string.loop_status) -> {
                val intent = Intent(this, LoopStatusActivity::class.java)
                startActivity(intent)
            }
            getString(R.string.menu_wizard)           -> startActivity(Intent(this, WizardActivity::class.java))
            getString(R.string.menu_status)           -> startActivity(Intent(this, StatusMenuActivity::class.java))
            getString(R.string.menu_prime_fill)       -> startActivity(Intent(this, FillMenuActivity::class.java))
            getString(R.string.menu_ecarb)            -> startActivity(Intent(this, ECarbActivity::class.java))
        }
    }
}