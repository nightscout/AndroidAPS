package app.aaps.plugins.automation.triggers

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.elements.VerticalTextView
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.dialogs.ChooseOperationDialog
import dagger.android.HasAndroidInjector
import org.json.JSONArray
import org.json.JSONObject
import java.util.Optional

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
                OR -> R.string.or
                XOR -> R.string.xor
                AND -> app.aaps.core.ui.R.string.and
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (t in values()) {
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

    fun pos(trigger: Trigger): Int {
        for (i in list.indices) {
            if (list[i] === trigger) return i
        }
        return -1
    }

    @Synchronized override fun shouldRun(): Boolean {
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

    override fun icon(): Optional<Int> = Optional.empty()

    override fun duplicate(): Trigger = TriggerConnector(injector, connectorType)

    override fun generateDialog(root: LinearLayout) {
        root.addView(
            LinearLayout(root.context).also { mainLayout ->
                mainLayout.orientation = LinearLayout.HORIZONTAL
                mainLayout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val padding = rh.dpToPx(3)
                mainLayout.setPadding(padding, padding, padding, padding)
                mainLayout.setBackgroundResource(R.drawable.border_automation_unit)
                // Header with spinner
                mainLayout.addView(createVerticalView(root.context))
                mainLayout.addView(
                    LinearLayout(root.context).also { rightSide ->
                        rightSide.orientation = LinearLayout.VERTICAL
                        rightSide.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        rightSide.addView(
                            LinearLayout(root.context).also {
                                it.orientation = LinearLayout.HORIZONTAL
                                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                it.addView(createAddButton(root.context, this))
                                it.addView(createDeleteButton(root.context, this))
                            })
                        // Child triggers
                        rightSide.addView(
                            LinearLayout(root.context).also {
                                it.orientation = LinearLayout.VERTICAL
                                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { params ->
                                    params.setMargins(rh.dpToPx(1), 0, rh.dpToPx(1), rh.dpToPx(2))
                                }
                                for (t in list) {
                                    t.generateDialog(it)
                                    it.addView(
                                        TextView(root.context).also { spacer ->
                                            spacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                            spacer.setPadding(0, rh.dpToPx(0.3f), 0, 0)
                                        })
                                }
                            })
                    })
            })
    }

    private fun createVerticalView(context: Context): VerticalTextView =
        VerticalTextView(context).apply {
            text = rh.gs(connectorType.stringRes)
            gravity = gravity or Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.automationOverlayColor))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).also { ll ->
                ll.setMargins(rh.dpToPx(3), rh.dpToPx(3), rh.dpToPx(3), rh.dpToPx(3))
            }
            setOnClickListener {
                scanForActivity(context)?.supportFragmentManager?.let {
                    ChooseOperationDialog().also { dialog ->
                        dialog.setCallback(object : ChooseOperationDialog.Callback() {
                            override fun run() {
                                result?.let { result ->
                                    setType(Type.values()[result])
                                    text = rh.gs(connectorType.stringRes)
                                }
                            }
                        })
                        dialog.setCheckedIndex(connectorType.ordinal)
                        dialog.show(it, "TriggerConnector")
                    }
                }
            }
        }
}