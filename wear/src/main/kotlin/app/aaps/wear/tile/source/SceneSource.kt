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
            aapsLogger.info(LTag.WEAR, "getSelectedActions: scene is active, showing End")
            return listOf(
                Action(
                    buttonText = context.resources.getString(R.string.scene_end),
                    iconRes = R.drawable.ic_cancel_red,
                    activityClass = BackgroundActionActivity::class.java.name,
                    action = EventData.ActionSceneStopPreCheck(),
                )
            )
        }
        val sceneData = getSceneData(sp)
        val slotValues = (1..4).map { sp.getString(preferenceKey(it), SLOT_AUTO) }

        // Slots explicitly pinned to a scene id are resolved first and removed from the auto pool,
        // so an "Automatic" slot never duplicates a scene another slot already claims.
        val explicitIds = slotValues.filterNot { it == SLOT_AUTO || it == SLOT_NONE }.toSet()
        val autoPool = sceneData.entries.filterNot { it.id in explicitIds }.iterator()

        val selectedEntries = slotValues.mapNotNull { value ->
            when (value) {
                SLOT_NONE -> null
                SLOT_AUTO -> if (autoPool.hasNext()) autoPool.next() else null
                else      -> sceneData.entries.find { it.id == value }
            }
        }
        return selectedEntries.map { entry ->
            Action(
                buttonText = entry.title,
                iconRes = R.drawable.ic_scene_purple,
                activityClass = BackgroundActionActivity::class.java.name,
                action = EventData.ActionScenePreCheck(entry.id, entry.title),
                message = context.resources.getString(R.string.action_scene_confirmation),
            ).also { aapsLogger.info(LTag.WEAR, """getSelectedActions: scene ${entry.title} id=${entry.id}""") }
        }
    }

    override fun getValidFor(): Long? = null

    /** Scene entries currently known to the watch, for the tile settings picker. */
    fun getSceneEntries(): List<EventData.SceneList.SceneEntry> = getSceneData(sp).entries

    private fun preferenceKey(index: Int) = "tile_scene_$index"

    private fun getSceneData(sp: SP): EventData.SceneList =
        EventData.deserialize(sp.getString(R.string.key_scene_data, EventData.SceneList(arrayListOf()).serialize())) as EventData.SceneList

    private fun isSceneActive(): Boolean {
        val raw = sp.getString(R.string.key_active_scene_state, "")
        if (raw.isEmpty()) return false
        return runCatching { (EventData.deserialize(raw) as? EventData.ActiveSceneState)?.active == true }.getOrDefault(false)
    }

    override fun getResourceReferences(resources: Resources): List<Int> = listOf(R.drawable.ic_scene_purple, R.drawable.ic_cancel_red)

    companion object {
        /** Slot not deliberately touched by the user — auto-fills with the next unclaimed scene. */
        const val SLOT_AUTO = "auto"

        /** Slot deliberately emptied by the user in the settings screen — stays empty. */
        const val SLOT_NONE = "none"
    }
}
