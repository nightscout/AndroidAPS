package app.aaps.plugins.automation.elements

class InputDuration(
    var value: Int = 0,
    var unit: TimeUnit = TimeUnit.MINUTES,
) {

    enum class TimeUnit {
        MINUTES, HOURS, DAYS
    }

    fun duplicate(): InputDuration {
        val i = InputDuration()
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

    override fun toString(): String = "InputDuration: $value $unit"
}
