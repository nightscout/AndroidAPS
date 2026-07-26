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
import app.aaps.wear.tile.source.TempTargetSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class TempTargetTileSettingsActivity : AppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var tempTargetSource: TempTargetSource

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        val options = tempTargetSource.getActions(resources)
            .map { TileSettingOption(it.settingName, it.buttonText ?: it.settingName) } +
            TileSettingOption("none", getString(R.string.tile_none))
        val defaults = tempTargetSource.getDefaultConfig()
        setContent {
            MaterialTheme {
                TempTargetTileSettingsScreen(sp = sp, options = options, defaults = defaults)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TileService.getUpdater(applicationContext).requestUpdate(TempTargetTileService::class.java)
    }
}

@Composable
private fun TempTargetTileSettingsScreen(sp: SP, options: List<TileSettingOption>, defaults: Map<String, String>) {
    val slots = remember {
        (1..4).map { i -> mutableStateOf(sp.getString("tile_tempt_$i", defaults["tile_tempt_$i"] ?: "none")) }
    }
    val labels = listOf(
        stringResource(R.string.tile_tempt_1),
        stringResource(R.string.tile_tempt_2),
        stringResource(R.string.tile_tempt_3),
        stringResource(R.string.tile_tempt_4)
    )
    val rows = (0..3).map { i ->
        TileSettingRow(
            label = labels[i],
            currentValue = slots[i].value,
            options = options,
            onSelect = { value ->
                slots[i].value = value
                sp.putString("tile_tempt_${i + 1}", value)
            }
        )
    }
    TileSettingsScreen(title = stringResource(R.string.tile_settings), rows = rows)
}
