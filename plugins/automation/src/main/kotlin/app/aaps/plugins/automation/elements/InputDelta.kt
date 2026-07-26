package app.aaps.plugins.automation.elements

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.R
import java.text.DecimalFormat

class InputDelta(private val rh: ResourceHelper) {

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
                for (d in DeltaType.entries) {
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
}
