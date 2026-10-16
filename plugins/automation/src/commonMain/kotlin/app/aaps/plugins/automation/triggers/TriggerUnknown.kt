package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.JsonObject

/**
 * A stored trigger this version cannot read: a type from a newer version or another fork, or data
 * that failed to parse.
 *
 * It is never true. An empty connector would be true, and then the rule would fire on every loop
 * cycle without the condition the user wrote.
 *
 * The stored JSON is written back unchanged, so saving the list here does not destroy the trigger
 * for a version that can read it. This matters when a client on an older version saves a rule
 * that a newer master created.
 */
class TriggerUnknown(deps: TriggerDeps, private val stored: JsonObject) : Trigger(deps) {

    private val type: String get() = stored.lenientString("type")

    override suspend fun shouldRun(): Boolean = false

    override fun toJSON(): String = stored.toString()

    override fun dataJSON(): JsonObject = stored["data"] as? JsonObject ?: JsonObject(emptyMap())

    override fun fromJSON(data: String): Trigger = this

    override fun friendlyName(): TextRef = AutomationStrings.triggerUnknownLabel

    override fun friendlyDescription(): String = rh.gs(AutomationStrings.triggerUnknownDesc, type)

    override fun duplicate(): Trigger = TriggerUnknown(deps, stored)
}
