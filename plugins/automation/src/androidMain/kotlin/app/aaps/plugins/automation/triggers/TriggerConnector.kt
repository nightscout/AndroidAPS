package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerConnector(deps: TriggerDeps) : Trigger(deps) {

    var list: MutableList<Trigger> = ArrayList()
    private var connectorType: Type = Type.AND

    enum class Type {
        AND, OR, XOR;

        fun apply(a: Boolean, b: Boolean): Boolean =
            when (this) {
                AND -> a && b
                OR  -> a || b
                XOR -> a xor b
            }

        val stringRes: TextRef
            get() = when (this) {
                OR  -> AutomationStrings.or
                XOR -> AutomationStrings.xor
                AND -> CoreUiStrings.and
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (t in Type.entries) {
                    list.add(rh.gs(t.stringRes))
                }
                return list
            }
        }
    }

    constructor(deps: TriggerDeps, connectorType: Type) : this(deps) {
        this.connectorType = connectorType
    }

    fun setType(type: Type) {
        connectorType = type
    }

    fun size(): Int = list.size

    override suspend fun shouldRun(): Boolean {
        var result = true
        // check first trigger
        if (list.isNotEmpty()) result = list[0].shouldRun()
        // check all others
        for (i in 1 until list.size) {
            result = connectorType.apply(result, list[i].shouldRun())
        }
        if (result) aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription().replace("\n", " "))
        return result
    }

    // Children are stored as JSON *strings* inside triggerList, not as nested objects. That is the
    // stored format every existing install has, so it stays.
    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("connectorType", connectorType.toString())
            put("triggerList", buildJsonArray { for (t in list) add(JsonPrimitive(t.toJSON())) })
        }

    /**
     * Builds one child from its stored JSON. Set by [TriggerFactory] right before [fromJSON], because
     * naming a trigger class is the factory's job, not this one's. Without it the children are
     * dropped, which is why only the factory ever calls [fromJSON].
     */
    var childFromJson: ((JsonObject) -> Trigger)? = null

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        connectorType = Type.valueOf(d.lenientString("connectorType", Type.AND.toString()))
        val array = d["triggerList"] as? JsonArray ?: JsonArray(emptyList())
        val build = childFromJson
        list.clear()
        for (element in array) {
            val child = (element as? JsonPrimitive)?.content ?: continue
            build?.invoke(jsonOf(child))?.let { list.add(it) }
        }
        return this
    }

    override fun friendlyName(): TextRef = connectorType.stringRes

    override fun friendlyDescription(): String {
        val result = StringBuilder()
        for ((counter, t) in list.withIndex()) {
            if (counter > 0)
                result.append("\n").append(rh.gs(friendlyName())).append("\n")
            result.append(t.friendlyDescription())
        }
        return result.toString()
    }

    override fun duplicate(): Trigger = TriggerConnector(deps, connectorType)
}