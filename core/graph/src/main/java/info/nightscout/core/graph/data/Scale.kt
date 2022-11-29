package info.nightscout.core.graph.data

class Scale(var shift: Double = 0.0, var multiplier: Double = 1.0) {

    fun transform(original: Double): Double {
        return original * multiplier + shift
    }
}