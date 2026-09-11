package app.aaps.wear.interaction.menus

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import app.aaps.shared.impl.weardata.ResFileMap
import app.aaps.shared.impl.weardata.toDrawable
import app.aaps.wear.data.ComplicationDataRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import app.aaps.wear.R
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace

class PreferenceMenuActivity : MenuListActivity() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

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
            // The Custom watch face's own settings - what its picture shows, not where it is shown.
            // Offered on every watch, whatever its make: these settings are what the Watch Face
            // Format face draws from, and on a watch whose firmware no longer runs code-based faces
            // this menu is the only way to reach them. Digital and Circle are not offered here:
            // neither exists in the Watch Face Format face, and a watch that can still run them can
            // reach their settings by long-pressing the face.
            add(MenuItem(R.drawable.watchface_custom, getString(R.string.label_watchface_custom), loadedWatchFaceImage()))
        }

    /**
     * The loaded zip's own picture, or null to fall back to the built-in artwork.
     *
     * Every valid zip carries one - it is what the phone's watch face list shows - so this row can
     * depict the design the wearer actually sent instead of a stock image that looks nothing like
     * their watch.
     *
     * Decoded through [toDrawable], which is the one place that knows every format a zip may carry.
     * This used to decode the bytes here and accept only PNG and JPG, so a zip whose main picture is
     * an SVG silently fell back to the stock image - and the wearer had no way to tell a missing
     * feature from a broken one.
     *
     * Read once, while the menu is being built, and failures are silent on purpose: an icon is not
     * worth a crash, and the fallback is a perfectly good picture.
     */
    private fun loadedWatchFaceImage(): Bitmap? = runCatching {
        val size = resources.displayMetrics.widthPixels
        runBlocking { complicationDataRepository.getCustomWatchface() }
            ?.resData?.get(ResFileMap.CUSTOM_WATCHFACE.fileName)
            ?.toDrawable(resources, size, size)
            ?.toBitmap(size, size)
    }.getOrNull()

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
        }
    }
}
