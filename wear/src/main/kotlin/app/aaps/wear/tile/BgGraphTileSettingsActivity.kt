package app.aaps.wear.tile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import dagger.android.AndroidInjection
import javax.inject.Inject

class BgGraphTileSettingsActivity : AppCompatActivity() {

    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BgGraphTileSettingsScreen(sp = sp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TileService.getUpdater(applicationContext).requestUpdate(BgGraphTileService::class.java)
    }
}

@Composable
private fun BgGraphTileSettingsScreen(sp: SP) {
    val tapAction = remember { mutableStateOf(sp.getString("tile_bg_graph_tap_action", "bg_graph")) }
    val hours = remember { mutableStateOf(sp.getString("tile_bg_graph_hours", "3")) }
    val hourUnit = stringResource(R.string.hour_short)
    val tapOptions = listOf(
        TileSettingOption("bg_graph", stringResource(R.string.tile_tap_bg_graph)),
        TileSettingOption("menu", stringResource(R.string.tile_tap_main_menu)),
        TileSettingOption("loop_status", stringResource(R.string.tile_tap_loop_status))
    )
    val hourOptions = listOf(
        TileSettingOption("1", "1$hourUnit"),
        TileSettingOption("3", "3$hourUnit"),
        TileSettingOption("6", "6$hourUnit"),
    )
    val rows = listOf(
        TileSettingRow(
            label = stringResource(R.string.tile_graph_time_range),
            currentValue = hours.value,
            options = hourOptions,
            onSelect = { value ->
                hours.value = value
                sp.putString("tile_bg_graph_hours", value)
            }
        ),
        TileSettingRow(
            label = stringResource(R.string.tile_tap_action),
            currentValue = tapAction.value,
            options = tapOptions,
            onSelect = { value ->
                tapAction.value = value
                sp.putString("tile_bg_graph_tap_action", value)
            }
        )
    )
    TileSettingsScreen(title = stringResource(R.string.tile_settings), rows = rows)
}
