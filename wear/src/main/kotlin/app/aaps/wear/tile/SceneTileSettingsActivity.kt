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
import app.aaps.wear.tile.source.SceneSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class SceneTileSettingsActivity : AppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var sceneSource: SceneSource

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        val liveOptions = sceneSource.getSceneEntries().map { TileSettingOption(it.id, it.title) }
        setContent {
            MaterialTheme {
                SceneTileSettingsScreen(sp = sp, liveOptions = liveOptions)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TileService.getUpdater(applicationContext).requestUpdate(SceneTileService::class.java)
    }
}

@Composable
private fun SceneTileSettingsScreen(sp: SP, liveOptions: List<TileSettingOption>) {
    val slots = remember {
        (1..4).map { i -> mutableStateOf(sp.getString("tile_scene_$i", SceneSource.SLOT_AUTO)) }
    }
    val labels = listOf(
        stringResource(R.string.tile_scene_1),
        stringResource(R.string.tile_scene_2),
        stringResource(R.string.tile_scene_3),
        stringResource(R.string.tile_scene_4)
    )
    val baseOptions = listOf(TileSettingOption(SceneSource.SLOT_AUTO, stringResource(R.string.tile_scene_auto))) +
        liveOptions +
        TileSettingOption(SceneSource.SLOT_NONE, stringResource(R.string.tile_none))
    val unavailableLabel = stringResource(R.string.tile_scene_unavailable)
    val rows = (0..3).map { i ->
        val currentValue = slots[i].value
        // A slot pinned to a scene that's since been deleted/disabled won't be in baseOptions —
        // add a synthetic entry so the picker shows "Unavailable" instead of the raw id.
        val options = if (baseOptions.any { it.value == currentValue }) baseOptions
                      else baseOptions + TileSettingOption(currentValue, unavailableLabel)
        TileSettingRow(
            label = labels[i],
            currentValue = currentValue,
            options = options,
            onSelect = { value ->
                slots[i].value = value
                sp.putString("tile_scene_${i + 1}", value)
            }
        )
    }
    TileSettingsScreen(title = stringResource(R.string.tile_settings), rows = rows)
}
