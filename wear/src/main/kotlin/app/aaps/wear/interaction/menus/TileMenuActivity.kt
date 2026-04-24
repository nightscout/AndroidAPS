package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.tile.ActionsTileSettingsActivity
import app.aaps.wear.tile.BgGraphTileSettingsActivity
import app.aaps.wear.tile.TempTargetTileSettingsActivity

class TileMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.pref_tile_settings)
        super.onCreate(savedInstanceState)
    }

    override fun provideElements(): List<MenuItem> =
        listOf(
            MenuItem(R.drawable.action_tile_preview, getString(R.string.label_actions_tile)),
            MenuItem(R.drawable.temp_target_tile_preview, getString(R.string.label_temp_target_tile)),
            MenuItem(R.drawable.bg_graph_tile, getString(R.string.label_bg_graph_tile)),
        )

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.label_actions_tile)     -> startActivity(Intent(this, ActionsTileSettingsActivity::class.java))
            getString(R.string.label_temp_target_tile) -> startActivity(Intent(this, TempTargetTileSettingsActivity::class.java))
            getString(R.string.label_bg_graph_tile)    -> startActivity(Intent(this, BgGraphTileSettingsActivity::class.java))
        }
    }
}
