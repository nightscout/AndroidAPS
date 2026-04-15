package app.aaps.plugins.automation.elements

import java.text.DecimalFormat

class InputDouble() {

    var value = 0.0
    private var minValue = 0.0
    private var maxValue = 0.0
    private var step = 0.0
    private var decimalFormat: DecimalFormat? = null

    constructor(value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: DecimalFormat) : this() {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
    }

    constructor(inputDouble: InputDouble) : this() {
        this.value = inputDouble.value
        this.minValue = inputDouble.minValue
        this.maxValue = inputDouble.maxValue
        this.step = inputDouble.step
        this.decimalFormat = inputDouble.decimalFormat
    }

    fun setValue(value: Double): InputDouble {
        this.value = value
        return this
    }
}
