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
import app.aaps.wear.tile.source.ActionSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class ActionsTileSettingsActivity : AppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var actionSource: ActionSource

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        val options = actionSource.getActions(resources)
            .map { TileSettingOption(it.settingName, it.settingLabel ?: it.buttonText ?: it.settingName) } +
            TileSettingOption("none", getString(R.string.tile_none))
        val defaults = actionSource.getDefaultConfig()
        setContent {
            MaterialTheme {
                ActionsTileSettingsScreen(sp = sp, options = options, defaults = defaults)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TileService.getUpdater(applicationContext).requestUpdate(ActionsTileService::class.java)
    }
}

@Composable
private fun ActionsTileSettingsScreen(sp: SP, options: List<TileSettingOption>, defaults: Map<String, String>) {
    val slots = remember {
        (1..4).map { i -> mutableStateOf(sp.getString("tile_action_$i", defaults["tile_action_$i"] ?: "none")) }
    }
    val labels = listOf(
        stringResource(R.string.tile_action_1),
        stringResource(R.string.tile_action_2),
        stringResource(R.string.tile_action_3),
        stringResource(R.string.tile_action_4)
    )
    val rows = (0..3).map { i ->
        TileSettingRow(
            label = labels[i],
            currentValue = slots[i].value,
            options = options,
            onSelect = { value ->
                slots[i].value = value
                sp.putString("tile_action_${i + 1}", value)
            }
        )
    }
    TileSettingsScreen(title = stringResource(R.string.tile_settings), rows = rows)
}
