package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.NumberPicker
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class InputDelta(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper

    enum class DeltaType {
        DELTA, SHORT_AVERAGE, LONG_AVERAGE;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                DELTA         -> R.string.delta
                SHORT_AVERAGE -> R.string.short_avgdelta
                LONG_AVERAGE  -> R.string.long_avgdelta
            }

        companion object {
            fun labels(resourceHelper: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (d in values()) {
                    list.add(resourceHelper.gs(d.stringRes))
                }
                return list
            }
        }
    }

    var value = 0.0
    private var minValue = 0.0
    private var maxValue = 0.0
    private var step = 0.0
    private var decimalFormat: DecimalFormat? = null
    var deltaType: DeltaType = DeltaType.DELTA

    constructor(injector: HasAndroidInjector, value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: DecimalFormat, deltaType: DeltaType) : this(injector) {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
        this.deltaType = deltaType
    }

    constructor(injector: HasAndroidInjector, inputDelta: InputDelta) : this(injector) {
        value = inputDelta.value
        minValue = inputDelta.minValue
        maxValue = inputDelta.maxValue
        step = inputDelta.step
        decimalFormat = inputDelta.decimalFormat
        deltaType = inputDelta.deltaType
    }

    override fun addToLayout(root: LinearLayout) {
        val spinner = Spinner(root.context)
        val spinnerArrayAdapter = ArrayAdapter(root.context, R.layout.spinner_centered, DeltaType.labels(resourceHelper))
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerArrayAdapter
        val spinnerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
        spinner.layoutParams = spinnerParams
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                deltaType = DeltaType.values()[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.setSelection(deltaType.ordinal)
        val numberPicker = NumberPicker(root.context, null)
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, null, null)
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value }
        val l = LinearLayout(root.context)
        l.orientation = LinearLayout.VERTICAL
        l.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.addView(spinner)
        l.addView(numberPicker)
        root.addView(l)
    }
}