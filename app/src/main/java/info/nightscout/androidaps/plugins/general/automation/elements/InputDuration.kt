package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.MinutesNumberPicker
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputDuration(injector: HasAndroidInjector) : Element(injector) {
    enum class TimeUnit {
        MINUTES, HOURS
    }

    constructor(injector: HasAndroidInjector, value: Int, unit: TimeUnit) : this(injector) {
        this.unit = unit
        this.value = value
    }

    var unit: TimeUnit = TimeUnit.MINUTES
    var value: Int = 0

    override fun addToLayout(root: LinearLayout) {
        val numberPicker : NumberPicker
        if (unit == TimeUnit.MINUTES) {
            numberPicker = MinutesNumberPicker(root.context, null)
            numberPicker.setParams(value.toDouble(), 5.0, 24 * 60.0, 10.0, DecimalFormat("0"), false, root.findViewById(R.id.ok))
        } else {
            numberPicker = NumberPicker(root.context, null)
            numberPicker.setParams(value.toDouble(), 1.0, 24.0, 1.0, DecimalFormat("0"), false, root.findViewById(R.id.ok))
        }
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value.toInt() }
        root.addView(numberPicker)
    }

    fun duplicate(): InputDuration {
        val i = InputDuration(injector)
        i.unit = unit
        i.value = value
        return i
    }

    fun getMinutes(): Int = if (unit == TimeUnit.MINUTES) value else value * 60

    fun setMinutes(value: Int): InputDuration {
        if (unit == TimeUnit.MINUTES)
            this.value = value
        else
            this.value = value / 60
        return this
    }
}