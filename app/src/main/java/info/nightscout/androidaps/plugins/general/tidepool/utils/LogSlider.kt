package info.nightscout.androidaps.plugins.general.tidepool.utils

object LogSlider {
    // logarithmic slider with positions start - end representing values start - end, calculate value at selected position
    fun calc(sliderStart: Int, sliderEnd: Int, start: Double, end: Double, position: Int): Double {
        var valueStart = start
        var valueEnd = end
        valueStart = Math.log(Math.max(1.0, valueStart))
        valueEnd = Math.log(Math.max(1.0, valueEnd))
        return Math.exp(valueStart + (valueEnd - valueStart) / (sliderEnd - sliderStart) * (position - sliderStart))
    }
}