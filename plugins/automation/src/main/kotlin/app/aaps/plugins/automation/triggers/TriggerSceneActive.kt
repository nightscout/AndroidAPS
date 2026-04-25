package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.ComparatorExists
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

/**
 * Triggers based on whether any scene is currently active.
 * Used as a precondition by [app.aaps.plugins.automation.actions.ActionRunScene]
 * to skip activation when a scene is already running.
 */
class TriggerSceneActive(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var sceneApi: SceneAutomationApi

    var comparator = ComparatorExists(rh)

    constructor(injector: HasAndroidInjector, compare: ComparatorExists.Compare) : this(injector) {
        comparator = ComparatorExists(rh, compare)
    }

    constructor(injector: HasAndroidInjector, other: TriggerSceneActive) : this(injector) {
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

    override fun dataJSON(): JSONObject =
        JSONObject().put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        comparator.value = ComparatorExists.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.trigger_scene_active
    override fun friendlyDescription(): String =
        rh.gs(R.string.trigger_scene_active_compared, rh.gs(comparator.value.stringRes))

    override fun composeIcon() = Icons.Filled.PlayArrow
    override fun composeIconTint() = IconTint.Scene

    override fun duplicate(): Trigger = TriggerSceneActive(injector, this)
}
