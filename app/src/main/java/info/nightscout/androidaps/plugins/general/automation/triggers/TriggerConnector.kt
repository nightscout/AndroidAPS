package info.nightscout.androidaps.plugins.general.automation.triggers

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.dialogs.TriggerListAdapter
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class TriggerConnector(mainApp: MainApp) : Trigger(mainApp) {
    var list: MutableList<Trigger> = ArrayList()
    var connectorType: Type = Type.AND

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
                AND -> R.string.and
            }

        fun labels(resourceHelper: ResourceHelper): List<String> {
            val list: MutableList<String> = ArrayList()
            for (t in values()) {
                list.add(resourceHelper.gs(t.stringRes))
            }
            return list
        }
    }

    constructor(mainApp: MainApp, connectorType: Type) : this(mainApp) {
        this.connectorType = connectorType
    }

    fun setType(type: Type) {
        connectorType = type
    }

    @Synchronized
    fun add(t: Trigger) {
        list.add(t)
        t.connector = this
    }

    @Synchronized
    fun add(pos: Int, t: Trigger) {
        list.add(pos, t)
        t.connector = this
    }

    @Synchronized
    fun remove(t: Trigger): Boolean = list.remove(t)

    fun size(): Int = list.size

    operator fun get(i: Int): Trigger = list[i]

    fun pos(trigger: Trigger): Int {
        for (i in list.indices) {
            if (list[i] === trigger) return i
        }
        return -1
    }

    @Synchronized override fun shouldRun(): Boolean {
        var result = true
        // check first trigger
        if (list.size > 0) result = list[0].shouldRun()
        // check all others
        for (i in 1 until list.size) {
            result = connectorType.apply(result, list[i].shouldRun())
        }
        if (result) aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription().replace("\n", " "))
        return result
    }

    @Synchronized override fun toJSON(): String {
        val array = JSONArray()
        for (t in list) array.put(t.toJSON())
        val data = JSONObject()
            .put("connectorType", connectorType.toString())
            .put("triggerList", array)
        return JSONObject()
            .put("type", TriggerConnector::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        connectorType = Type.valueOf(safeGetString(d, "connectorType")!!)
        val array = d.getJSONArray("triggerList")
        list.clear()
        for (i in 0 until array.length()) {
            instantiate(JSONObject(array.getString(i)))?.let {
                add(it)
            }
        }
        return this
    }

    override fun friendlyName(): Int = connectorType.stringRes

    override fun friendlyDescription(): String {
        val result = StringBuilder()
        for ((counter, t) in list.withIndex()) {
            if (counter > 0)
                result.append("\n").append(resourceHelper.gs(friendlyName())).append("\n")
            result.append(t.friendlyDescription())
        }
        return result.toString()
    }

    override fun icon(): Optional<Int?> = Optional.absent()

    override fun duplicate(): Trigger = TriggerConnector(mainApp, connectorType)

    private var adapter: TriggerListAdapter? = null

    fun rebuildView(fragmentManager: FragmentManager) = adapter?.rebuild(fragmentManager)

    override fun generateDialog(root: LinearLayout) {
        val padding = resourceHelper.dpToPx(5)
        root.setPadding(padding, padding, padding, padding)
        root.setBackgroundResource(R.drawable.border_automation_unit)
        val triggerListLayout = LinearLayout(root.context)
        triggerListLayout.orientation = LinearLayout.VERTICAL
        triggerListLayout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(triggerListLayout)
        adapter = TriggerListAdapter(mainApp, resourceHelper, root.context, triggerListLayout, this)
    }

    fun simplify(): TriggerConnector { // simplify children
        for (i in 0 until size()) {
            if (get(i) is TriggerConnector) {
                val t = get(i) as TriggerConnector
                t.simplify()
            }
        }
        // drop connector with only 1 element
        if (size() == 1 && get(0) is TriggerConnector) {
            val c = get(0) as TriggerConnector
            remove(c)
            connectorType = c.connectorType
            for (t in c.list) add(t)
            c.list.clear()
            return simplify()
        }
        // merge connectors
        connector?.let { connector ->
            if (connector.connectorType == connectorType || size() == 1) {
                val pos = connector.pos(this)
                connector.remove(this)
                // move triggers of child connector into parent connector
                for (i in size() - 1 downTo 0) {
                    connector.add(pos, get(i))
                }
                list.clear()
                return connector.simplify()
            }
        }
        return this
    }
}