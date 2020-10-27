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

class InputLocationMode(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper

    enum class Mode {
        INSIDE, OUTSIDE, GOING_IN, GOING_OUT;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                INSIDE    -> R.string.location_inside
                OUTSIDE   -> R.string.location_outside
                GOING_IN  -> R.string.location_going_in
                GOING_OUT -> R.string.location_going_out
            }

        fun fromString(wanted: String): Mode {
            for (c in values()) {
                if (c.toString() === wanted) return c
            }
            throw IllegalStateException("Invalid parameter")
        }

        companion object {
            fun labels(resourceHelper: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in values()) {
                    list.add(resourceHelper.gs(c.stringRes))
                }
                return list
            }
        }
    }

    var value: Mode = Mode.INSIDE

    constructor(injector: HasAndroidInjector, value: InputLocationMode.Mode) : this(injector) {
        this.value = value
    }

    override fun addToLayout(root: LinearLayout) {
        val adapter = ArrayAdapter(root.context, R.layout.spinner_centered, Mode.labels(resourceHelper))
        val spinner = Spinner(root.context)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val spinnerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
        spinner.layoutParams = spinnerParams
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value = Mode.values()[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.setSelection(value.ordinal)
        root.addView(spinner)
    }
}