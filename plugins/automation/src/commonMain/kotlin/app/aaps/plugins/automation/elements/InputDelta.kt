package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.resources.TextResolver

class InputDelta(private val rh: TextResolver) {

    enum class DeltaType {
        DELTA, SHORT_AVERAGE, LONG_AVERAGE;

        val stringRes: TextRef
            get() = when (this) {
                DELTA -> AutomationStrings.delta
                SHORT_AVERAGE -> AutomationStrings.short_avgdelta
                LONG_AVERAGE -> AutomationStrings.long_avgdelta
            }

        companion object {

            fun labels(rh: TextResolver): List<String> {
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
    private var decimalFormat: NumberFormat? = null
    var deltaType: DeltaType = DeltaType.DELTA

    constructor(rh: TextResolver, value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: NumberFormat, deltaType: DeltaType) : this(rh) {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
        this.deltaType = deltaType
    }

    constructor(rh: TextResolver, inputDelta: InputDelta) : this(rh) {
        value = inputDelta.value
        minValue = inputDelta.minValue
        maxValue = inputDelta.maxValue
        step = inputDelta.step
        decimalFormat = inputDelta.decimalFormat
        deltaType = inputDelta.deltaType
    }
}
