package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject

class ComparatorExists(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper

    enum class Compare {
        EXISTS, NOT_EXISTS;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                EXISTS     -> R.string.exists
                NOT_EXISTS -> R.string.notexists
            }

        companion object {
            fun labels(resourceHelper: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in values()) list.add(resourceHelper.gs(c.stringRes))
                return list
            }
        }
    }

    constructor(injector: HasAndroidInjector, value: Compare) : this(injector) {
        this.value = value
    }

    var value = Compare.EXISTS

    override fun addToLayout(root: LinearLayout) {
        val spinner = Spinner(root.context)
        val spinnerArrayAdapter = ArrayAdapter(root.context, R.layout.spinner_centered, Compare.labels(resourceHelper))
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerArrayAdapter
        val spinnerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        spinnerParams.setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
        spinner.layoutParams = spinnerParams
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value = Compare.values()[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.setSelection(value.ordinal)
        root.addView(spinner)
    }
}