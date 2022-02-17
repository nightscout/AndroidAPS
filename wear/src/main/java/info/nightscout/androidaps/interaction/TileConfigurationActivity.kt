package info.nightscout.androidaps.interaction

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.wear.tiles.TileService
import preference.WearPreferenceActivity
import info.nightscout.androidaps.tile.ActionsTileService
import info.nightscout.androidaps.tile.TempTargetTileService

var TAG = "ASTAG-config"

class TileConfigurationActivity : WearPreferenceActivity() {

    private var configFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Tile"
        configFileName = intent.action
        val resXmlId = resources.getIdentifier(configFileName, "xml", applicationContext.packageName)
        Log.d("ConfigurationActivity::onCreate --->> getIntent().getAction()", configFileName!!)
        Log.d("ConfigurationActivity::onCreate --->> resXmlId", resXmlId.toString())
        addPreferencesFromResource(resXmlId)
        val view = window.decorView as ViewGroup
        view.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note that TileService updates are hard limited to once every 20 seconds.
        if (configFileName === "tile_configuration_activity") {
            Log.i(TAG, "onDestroy a: requestUpdate!!")
            TileService.getUpdater(this)
                .requestUpdate(ActionsTileService::class.java)
        } else if (configFileName === "tile_configuration_tempt") {
            Log.i(TAG, "onDestroy tt: requestUpdate!!")
            TileService.getUpdater(this)
                .requestUpdate(TempTargetTileService::class.java)
        } else {
            Log.i(TAG, "onDestroy : NO tile service available for $configFileName")
        }
    }
}
