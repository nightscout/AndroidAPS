package info.nightscout.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import info.nightscout.automation.R
import info.nightscout.core.ui.elements.NumberPicker
import info.nightscout.shared.interfaces.ResourceHelper
import java.text.DecimalFormat

class InputDelta(private val rh: ResourceHelper) : Element() {

    enum class DeltaType {
        DELTA, SHORT_AVERAGE, LONG_AVERAGE;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                DELTA -> R.string.delta
                SHORT_AVERAGE -> R.string.short_avgdelta
                LONG_AVERAGE -> R.string.long_avgdelta
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (d in values()) {
                    list.add(rh.gs(d.stringRes))
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

    constructor(rh: ResourceHelper, value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: DecimalFormat, deltaType: DeltaType) : this(rh) {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
        this.deltaType = deltaType
    }

    constructor(rh: ResourceHelper, inputDelta: InputDelta) : this(rh) {
        value = inputDelta.value
        minValue = inputDelta.minValue
        maxValue = inputDelta.maxValue
        step = inputDelta.step
        decimalFormat = inputDelta.decimalFormat
        deltaType = inputDelta.deltaType
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, info.nightscout.core.ui.R.layout.spinner_centered, DeltaType.labels(rh)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        deltaType = DeltaType.values()[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                setSelection(deltaType.ordinal)
                gravity = Gravity.CENTER_HORIZONTAL
            })
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, minValue, maxValue, step, decimalFormat, true, null, null)
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}