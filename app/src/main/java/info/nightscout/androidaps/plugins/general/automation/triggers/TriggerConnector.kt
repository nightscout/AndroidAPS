package info.nightscout.androidaps.plugins.general.automation.triggers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

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
                AND -> R.string.and
            }

        companion object {
            fun labels(resourceHelper: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (t in values()) {
                    list.add(resourceHelper.gs(t.stringRes))
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
                result.append("\n").append(resourceHelper.gs(friendlyName())).append("\n")
            result.append(t.friendlyDescription())
        }
        return result.toString()
    }

    override fun icon(): Optional<Int?> = Optional.absent()

    override fun duplicate(): Trigger = TriggerConnector(injector, connectorType)

    override fun generateDialog(root: LinearLayout) {
        val padding = resourceHelper.dpToPx(5)
        root.setPadding(padding, padding, padding, padding)
        root.setBackgroundResource(R.drawable.border_automation_unit)
        // Header with spinner
        val headerLayout = LinearLayout(root.context)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        headerLayout.addView(createSpinner(root.context))
        headerLayout.addView(createAddButton(root.context, this))
        headerLayout.addView(createDeleteButton(root.context, this))
        root.addView(headerLayout)
        // Child triggers
        val listLayout = LinearLayout(root.context)
        listLayout.orientation = LinearLayout.VERTICAL
        listLayout.setBackgroundColor(resourceHelper.gc(R.color.mdtp_line_dark))
        //listLayout.setPadding(resourceHelper.dpToPx(5), resourceHelper.dpToPx(5), resourceHelper.dpToPx(5), 0)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(resourceHelper.dpToPx(15), 0, resourceHelper.dpToPx(5), resourceHelper.dpToPx(4))
        listLayout.layoutParams = params
        for (t in list) t.generateDialog(listLayout)
        root.addView(listLayout)
    }

    private fun createSpinner(context: Context): Spinner {
        val initialPosition = connectorType.ordinal
        val spinner = Spinner(context)
        val spinnerArrayAdapter = ArrayAdapter(context, R.layout.spinner_centered, Type.labels(resourceHelper))
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerArrayAdapter
        spinner.setSelection(initialPosition)
        spinner.setBackgroundColor(resourceHelper.gc(R.color.black_overlay))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, resourceHelper.dpToPx(8), 0, resourceHelper.dpToPx(8))
        params.weight = 1.0f
        spinner.layoutParams = params
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setType(Type.values()[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return spinner
    }
}