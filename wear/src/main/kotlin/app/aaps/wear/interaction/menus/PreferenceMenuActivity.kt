package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
import app.aaps.wear.R
import app.aaps.wear.interaction.SamsungWatchFaceEditor
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace

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
            add(MenuItem(R.drawable.ic_tile_settings, getString(R.string.pref_tile_settings)))
            add(MenuItem(R.drawable.ic_complication, getString(R.string.pref_complication_settings)))
            add(MenuItem(R.drawable.ic_others, getString(R.string.pref_others_settings)))
            // These 3 items only make sense where SysUI can hand off to a live editing session
            if (SamsungWatchFaceEditor.isAvailable(this@PreferenceMenuActivity)) {
                add(MenuItem(R.drawable.watchface_custom, getString(R.string.label_watchface_custom)))
                add(MenuItem(R.drawable.watchface_digitalstyle, getString(R.string.label_watchface_digital_style)))
                add(MenuItem(R.drawable.watchface_circle, getString(R.string.label_watchface_circle)))
            }
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.pref_display_settings)      -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_preference_id), R.xml.display_preferences)
            })

            getString(R.string.pref_graph_settings)        -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_preference_id), R.xml.graph_preferences)
            })

            getString(R.string.pref_interface_settings)    -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_preference_id), R.xml.interface_preferences)
            })

            getString(R.string.pref_tile_settings)         -> startActivity(Intent(this, TileMenuActivity::class.java))

            getString(R.string.pref_complication_settings) -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_preference_id), R.xml.complication_preferences)
            })

            getString(R.string.pref_others_settings)       -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_preference_id), R.xml.others_preferences)
            })

            getString(R.string.label_watchface_custom)     -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_selected_watchface), SelectedWatchFace.CUSTOM.ordinal)
            })
            getString(R.string.label_watchface_digital_style) -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_selected_watchface), SelectedWatchFace.DIGITAL.ordinal)
            })
            getString(R.string.label_watchface_circle)     -> startActivity(Intent(this, WatchfaceConfigurationActivity::class.java).apply {
                putExtra(getString(R.string.key_selected_watchface), SelectedWatchFace.CIRCLE.ordinal)
            })
        }
    }
}
