package app.aaps.wear.interaction

import android.os.Bundle
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.preference.WearPreferenceActivity
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.TempTargetTileService
import dagger.android.AndroidInjection
import javax.inject.Inject

class TileConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    private var configFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        // SET configFileName BEFORE calling super.onCreate()
        configFileName = intent.action

        // Now call super - which will call createPreferenceFragment()
        super.onCreate(savedInstanceState)
        title = "Tile"

        val view = window.decorView as ViewGroup
        view.requestFocus()

        // Set background after super.onCreate()
        window.decorView.setBackgroundResource(R.drawable.settings_background)

        // Add padding to the content view for spacing from top and bottom
        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView?.setPadding(0, 30, 0, 30)
    }

    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        // Map action strings to resource IDs directly instead of using reflection
        val resXmlId = when (configFileName) {
            "tile_configuration_activity" -> R.xml.tile_configuration_activity
            "tile_configuration_tempt"    -> R.xml.tile_configuration_tempt
            else -> {
                0
            }
        }

        aapsLogger.debug(LTag.WEAR, "TileConfigurationActivity::createPreferenceFragment --->> getIntent().getAction() $configFileName")
        aapsLogger.debug(LTag.WEAR, "TileConfigurationActivity::createPreferenceFragment --->> resXmlId $resXmlId")

        return TileConfigurationFragment.newInstance(resXmlId)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Determine which tile service to update based on the action
        val tileServiceClass = when (configFileName) {
            "tile_configuration_activity" -> {
                aapsLogger.info(LTag.WEAR, "onDestroy: scheduling ActionsTileService update")
                ActionsTileService::class.java
            }
            "tile_configuration_tempt" -> {
                aapsLogger.info(LTag.WEAR, "onDestroy: scheduling TempTargetTileService update")
                TempTargetTileService::class.java
            }
            else -> {
                aapsLogger.info(LTag.WEAR, "onDestroy: NO tile service available for $configFileName")
                null
            }
        }

        // Use applicationContext instead of 'this' to avoid leaking the service connection
        // Note that TileService updates are hard limited to once every 20 seconds.
        tileServiceClass?.let { serviceClass ->
            try {
                TileService.getUpdater(applicationContext).requestUpdate(serviceClass)
                aapsLogger.info(LTag.WEAR, "onDestroy: successfully requested tile update")
            } catch (e: Exception) {
                aapsLogger.error(LTag.WEAR, "onDestroy: failed to request tile update - ${e.message}", e)
            }
        }
    }

    /**
     * Fragment for loading tile configuration preferences
     */
    class TileConfigurationFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val resXmlId = arguments?.getInt(ARG_XML_RES_ID) ?: 0
            if (resXmlId != 0) {
                setPreferencesFromResource(resXmlId, rootKey)
            }
        }

        companion object {
            private const val ARG_XML_RES_ID = "xml_res_id"

            fun newInstance(xmlResId: Int): TileConfigurationFragment {
                return TileConfigurationFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_XML_RES_ID, xmlResId)
                    }
                }
            }
        }
    }
}