package app.aaps.plugins.automation.triggers

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

abstract class Trigger(val deps: TriggerDeps) {

    // Exposed from [deps] so the 29 subclass bodies read exactly as they did before the deps
    // was removed - only their constructors changed.
    val aapsLogger get() = deps.aapsLogger
    val rxBus get() = deps.rxBus
    val rh get() = deps.rh
    val profileFunction get() = deps.profileFunction
    val profileUtil get() = deps.profileUtil
    val preferences get() = deps.preferences
    val lastKnownLocation get() = deps.lastKnownLocation
    val persistenceLayer get() = deps.persistenceLayer
    val activePlugin get() = deps.activePlugin
    val iobCobCalculator get() = deps.iobCobCalculator
    val glucoseStatusProvider get() = deps.glucoseStatusProvider
    val dateUtil get() = deps.dateUtil

    abstract suspend fun shouldRun(): Boolean
    abstract fun dataJSON(): JsonObject
    abstract fun fromJSON(data: String): Trigger

    /**
     * Parses a stored `data` block. Returns an empty object on anything unparseable, so a single bad
     * trigger degrades to its defaults instead of taking the whole automation list down - the same
     * thing org.json's lenient readers did before.
     */
    protected fun jsonOf(data: String): JsonObject =
        runCatching { Json.parseToJsonElement(data).jsonObject }.getOrElse { JsonObject(emptyMap()) }

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String

    /**
     * Compose-native icon. Override in leaf triggers to return a Material-Icons
     * [ImageVector] or a project `Ic*`. Default: null (connector / dummy).
     */
    open fun composeIcon(): ImageVector = elementType().icon()

    /** Semantic UI type for this trigger. Used to resolve theme-aware colors and icons. */
    open fun elementType(): ElementType = ElementType.AUTOMATION

    abstract fun duplicate(): Trigger

    fun toJSON(): String =
        buildJsonObject {
            put("type", this@Trigger::class.java.simpleName)
            put("data", dataJSON())
        }.toString()


}