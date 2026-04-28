package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.TileSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneSource @Inject constructor(private val context: Context, private val sp: SP, private val aapsLogger: AAPSLogger) : TileSource {

    override fun getSelectedActions(): List<Action> {
        if (isSceneActive()) {
            aapsLogger.info(LTag.WEAR, "getSelectedActions: scene is active, showing STOP")
            return listOf(
                Action(
                    buttonText = context.resources.getString(R.string.scene_stop),
                    iconRes = R.drawable.ic_cancel,
                    activityClass = BackgroundActionActivity::class.java.name,
                    action = EventData.ActionSceneStop(),
                    message = context.resources.getString(R.string.scene_stop)
                )
            )
        }
        val sceneList = mutableListOf<Action>()
        val sceneData = getSceneData(sp)

        for (entry in sceneData.entries) {
            if (sceneList.size < 4) {
                sceneList.add(
                    Action(
                        buttonText = entry.title,
                        iconRes = R.drawable.ic_scene,
                        activityClass = BackgroundActionActivity::class.java.name,
                        action = EventData.ActionScenePreCheck(entry.id, entry.title),
                        message = context.resources.getString(R.string.action_scene_confirmation)
                    )
                )
                aapsLogger.info(LTag.WEAR, """getSelectedActions: scene ${entry.title} id=${entry.id}""")
            }
        }
        return sceneList
    }

    override fun getValidFor(): Long? = null

    private fun getSceneData(sp: SP): EventData.SceneList =
        EventData.deserialize(sp.getString(R.string.key_scene_data, EventData.SceneList(arrayListOf()).serialize())) as EventData.SceneList

    private fun isSceneActive(): Boolean {
        val raw = sp.getString(R.string.key_active_scene_state, "")
        if (raw.isEmpty()) return false
        return runCatching { (EventData.deserialize(raw) as? EventData.ActiveSceneState)?.active == true }.getOrDefault(false)
    }

    override fun getResourceReferences(resources: Resources): List<Int> = listOf(R.drawable.ic_scene, R.drawable.ic_cancel)
}
