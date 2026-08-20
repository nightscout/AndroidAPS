package app.aaps.plugins.automation.actions

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.automation.triggers.Trigger
import org.json.JSONObject
import javax.inject.Provider

/**
 * One step an automation performs.
 *
 * Actions are built from JSON rather than by Dagger - the automation list is stored as a string and
 * each entry names its type - so they used to reach back through `HasAndroidInjector` and inject
 * themselves. That needs a generated members injector, which is Java, so it cannot happen in a
 * multiplatform module; and self-injection hides what an action actually depends on.
 *
 * Dependencies now arrive through the constructor, supplied by [ActionFactory], which owns the
 * type-name-to-constructor mapping. The three below are needed by every action; anything else a
 * specific action needs is on its own constructor, where it is visible.
 */
abstract class Action(
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper,
    val pumpEnactResultProvider: Provider<PumpEnactResult>
) {

    open var precondition: Trigger? = null

    abstract fun friendlyName(): Int
    abstract fun shortDescription(): String
    abstract suspend fun doAction(): PumpEnactResult
    abstract fun isValid(): Boolean

    /**
     * Compose-native icon. Override in leaf actions to return a Material-Icons
     * [ImageVector] or a project `Ic*`.
     */
    open fun composeIcon(): ImageVector = elementType().icon()

    /** Semantic UI type for this action. Used to resolve theme-aware colors and icons. */
    open fun elementType(): ElementType = ElementType.AAPS

    var title = ""

    open fun hasDialog(): Boolean = false

    open fun toJSON(): String =
        JSONObject().put("type", this.javaClass.simpleName).toString()

    open fun fromJSON(data: String): Action = this

    fun apply(a: Action) {
        val obj = JSONObject(a.toJSON())
        val type = obj.getString("type")
        val data = obj.getJSONObject("data")
        if (type == javaClass.simpleName) fromJSON(data.toString())
    }

}
