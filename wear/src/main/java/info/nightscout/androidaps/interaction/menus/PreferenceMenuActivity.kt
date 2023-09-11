package info.nightscout.androidaps.interaction.menus

import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.WatchfaceConfigurationActivity
import info.nightscout.androidaps.interaction.utils.MenuListActivity

class PreferenceMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.menu_settings)
        super.onCreate(savedInstanceState)
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            add(MenuItem(R.drawable.ic_display, getString(R.string.pref_display_settings)))
            add(MenuItem(R.drawable.ic_graph, getString(R.string.pref_graph_settings)))
            add(MenuItem(R.drawable.ic_interface, getString(R.string.pref_interface_settings)))
            add(MenuItem(R.drawable.ic_complication, getString(R.string.pref_complication_settings)))
            add(MenuItem(R.drawable.ic_others, getString(R.string.pref_others_settings)))
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.pref_display_settings)    -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(getString(R.string.key_preference_id), R.xml.display_preferences)
            })
            getString(R.string.pref_graph_settings)        -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(getString(R.string.key_preference_id), R.xml.graph_preferences)
            })
            getString(R.string.pref_interface_settings)    -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(getString(R.string.key_preference_id), R.xml.interface_preferences)
            })
            getString(R.string.pref_complication_settings) -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(getString(R.string.key_preference_id), R.xml.complication_preferences)
            })
            getString(R.string.pref_others_settings) -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(getString(R.string.key_preference_id), R.xml.others_preferences)
            })
        }
    }
}
