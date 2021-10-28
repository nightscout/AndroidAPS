package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputDelta(private val resourceHelper: ResourceHelper) : Element() {

    enum class DeltaType {
        DELTA, SHORT_AVERAGE, LONG_AVERAGE;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                DELTA -> R.string.delta
                SHORT_AVERAGE -> R.string.short_avgdelta
                LONG_AVERAGE -> R.string.long_avgdelta
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

    constructor(resourceHelper: ResourceHelper, value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: DecimalFormat, deltaType: DeltaType) : this(resourceHelper) {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
        this.deltaType = deltaType
    }

    constructor(resourceHelper: ResourceHelper, inputDelta: InputDelta) : this(resourceHelper) {
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
                adapter = ArrayAdapter(root.context, R.layout.spinner_centered, DeltaType.labels(resourceHelper)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
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
            NumberPicker(root.context, null).apply {
                setParams(value, minValue, maxValue, step, decimalFormat, true, null, null)
                setOnValueChangedListener { value: Double -> this.value = value }
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}