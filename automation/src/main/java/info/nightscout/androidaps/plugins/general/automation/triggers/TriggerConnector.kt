package info.nightscout.androidaps.plugins.general.automation.triggers

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseOperationDialog
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.ui.VerticalTextView
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
                OR -> R.string.or
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
                result.append("\n").append(resourceHelper.gs(friendlyName())).append("\n")
            result.append(t.friendlyDescription())
        }
        return result.toString()
    }

    override fun icon(): Optional<Int?> = Optional.absent()

    override fun duplicate(): Trigger = TriggerConnector(injector, connectorType)

    override fun generateDialog(root: LinearLayout) {
        val mainLayout = LinearLayout(root.context).also {
            it.orientation = LinearLayout.HORIZONTAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val padding = resourceHelper.dpToPx(3)
        mainLayout.setPadding(padding, padding, padding, padding)
        mainLayout.setBackgroundResource(R.drawable.border_automation_unit)

        val buttonLayout = LinearLayout(root.context).also {
            it.orientation = LinearLayout.HORIZONTAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        buttonLayout.addView(createAddButton(root.context, this))
        buttonLayout.addView(createDeleteButton(root.context, this))

        val rightSideLayout = LinearLayout(root.context).also {
            it.orientation = LinearLayout.VERTICAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        rightSideLayout.addView(buttonLayout)

        // Child triggers
        val listLayout = LinearLayout(root.context).also {
            it.orientation = LinearLayout.VERTICAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { params ->
                params.setMargins(resourceHelper.dpToPx(1), 0, resourceHelper.dpToPx(1), resourceHelper.dpToPx(2))
            }
        }
        for (t in list) {
            t.generateDialog(listLayout)
            listLayout.addView(
                TextView(root.context).also {
                    it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    it.setPadding(0, resourceHelper.dpToPx(0.3f), 0, 0)
                })
        }
        rightSideLayout.addView(listLayout)

        // Header with spinner
        mainLayout.addView(createVerticalView(root.context))
        mainLayout.addView(rightSideLayout)
        root.addView(mainLayout)
    }

    private fun createVerticalView(context: Context): VerticalTextView =
        VerticalTextView(context).apply {
            text = resourceHelper.gs(connectorType.stringRes)
            gravity = gravity or Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(resourceHelper.gc(R.color.black_overlay))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).also { ll ->
                ll.setMargins(resourceHelper.dpToPx(3), resourceHelper.dpToPx(3), resourceHelper.dpToPx(3), resourceHelper.dpToPx(3))
            }
            setOnClickListener {
                scanForActivity(context)?.supportFragmentManager?.let {
                    ChooseOperationDialog().also { dialog ->
                        dialog.setCallback(object : ChooseOperationDialog.Callback() {
                            override fun run() {
                                result?.let { result ->
                                    setType(Type.values()[result])
                                    text = resourceHelper.gs(connectorType.stringRes)
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