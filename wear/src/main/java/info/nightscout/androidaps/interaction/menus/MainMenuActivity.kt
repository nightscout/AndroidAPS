package info.nightscout.androidaps.interaction.menus

import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.androidaps.interaction.WatchfaceConfigurationActivity
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity
import info.nightscout.androidaps.interaction.actions.TreatmentActivity
import info.nightscout.androidaps.interaction.actions.WizardActivity
import info.nightscout.androidaps.interaction.utils.MenuListActivity
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.EventData.ActionResendData

class MainMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.label_actions_activity)
        super.onCreate(savedInstanceState)
        rxBus.send(EventWearToMobile(ActionResendData("MainMenuListActivity")))
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            if (!sp.getBoolean(R.string.key_wear_control, false)) {
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_sync, getString(R.string.menu_resync)))
            } else {
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
            getString(R.string.menu_settings)         -> startActivity(Intent(this, PreferenceMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_resync)           -> rxBus.send(EventWearToMobile(ActionResendData("Re-Sync")))
            getString(R.string.status_profile_switch) -> rxBus.send(EventWearToMobile(EventData.ActionProfileSwitchSendInitialData(System.currentTimeMillis())))
            getString(R.string.menu_tempt)            -> startActivity(Intent(this, TempTargetActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_treatment)        -> startActivity(Intent(this, TreatmentActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_wizard)           -> startActivity(Intent(this, WizardActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_status)           -> startActivity(Intent(this, StatusMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_prime_fill)       -> startActivity(Intent(this, FillMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_ecarb)            -> startActivity(Intent(this, ECarbActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
