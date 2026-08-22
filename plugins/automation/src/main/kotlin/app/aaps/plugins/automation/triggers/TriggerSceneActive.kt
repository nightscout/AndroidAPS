package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorExists
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Triggers based on whether any scene is currently active.
 * Used as a precondition by [app.aaps.plugins.automation.actions.ActionRunScene]
 * to skip activation when a scene is already running.
 */
class TriggerSceneActive(
    deps: TriggerDeps,
    private val sceneApi: SceneAutomationApi
) : Trigger(deps) {


    var comparator = ComparatorExists(rh)

    constructor(deps: TriggerDeps, sceneApi: SceneAutomationApi, compare: ComparatorExists.Compare) : this(deps, sceneApi) {
        comparator = ComparatorExists(rh, compare)
    }

    constructor(deps: TriggerDeps, sceneApi: SceneAutomationApi, other: TriggerSceneActive) : this(deps, sceneApi) {
        comparator = ComparatorExists(rh, other.comparator.value)
    }

    override suspend fun shouldRun(): Boolean {
        val active = sceneApi.isAnySceneActive()
        val ready = (active && comparator.value == ComparatorExists.Compare.EXISTS) ||
            (!active && comparator.value == ComparatorExists.Compare.NOT_EXISTS)
        aapsLogger.debug(
            LTag.AUTOMATION,
            (if (ready) "Ready for execution: " else "NOT ready for execution: ") + friendlyDescription()
        )
        return ready
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject { put("comparator", comparator.value.toString()) }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        comparator.value = ComparatorExists.Compare.valueOf(d.lenientStringOrNull("comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.trigger_scene_active
    override fun friendlyDescription(): String =
        rh.gs(R.string.trigger_scene_active_compared, rh.gs(comparator.value.stringRes))

    override fun composeIcon() = Icons.Filled.PlayArrow
    override fun elementType() = ElementType.SCENE

    override fun duplicate(): Trigger = TriggerSceneActive(deps, sceneApi, this)
}
