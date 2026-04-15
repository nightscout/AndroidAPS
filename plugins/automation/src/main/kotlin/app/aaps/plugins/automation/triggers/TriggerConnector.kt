package app.aaps.plugins.automation.triggers

import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import org.json.JSONArray
import org.json.JSONObject

class TriggerConnector(injector: HasAndroidInjector) : Trigger(injector) {

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

        @get:StringRes val stringRes: Int
            get() = when (this) {
                OR  -> R.string.or
                XOR -> R.string.xor
                AND -> app.aaps.core.ui.R.string.and
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

    constructor(injector: HasAndroidInjector, connectorType: Type) : this(injector) {
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

    override fun dataJSON(): JSONObject {
        val array = JSONArray()
        for (t in list) array.put(t.toJSON())
        return JSONObject()
            .put("connectorType", connectorType.toString())
            .put("triggerList", array)
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        connectorType = Type.valueOf(safeGetString(d, "connectorType")!!)
        val array = d.getJSONArray("triggerList")
        list.clear()
        for (i in 0 until array.length()) {
            instantiate(JSONObject(array.getString(i))).let {
                list.add(it)
            }
        }
        return this
    }

    override fun friendlyName(): Int = connectorType.stringRes

    override fun friendlyDescription(): String {
        val result = StringBuilder()
        for ((counter, t) in list.withIndex()) {
            if (counter > 0)
                result.append("\n").append(rh.gs(friendlyName())).append("\n")
            result.append(t.friendlyDescription())
        }
        return result.toString()
    }

    override fun duplicate(): Trigger = TriggerConnector(injector, connectorType)
}