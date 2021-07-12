package info.nightscout.androidaps.plugins.general.overview.graphExtensions

class Scale(var shift: Double = 0.0, var multiplier: Double = 0.0) {

    fun transform(original: Double): Double {
        return original * multiplier + shift
    }
}