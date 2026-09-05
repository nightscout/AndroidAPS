package app.aaps.core.interfaces.graph

class Scale(var shift: Double = 0.0, var multiplier: Double = 1.0) {

    fun transform(original: Double): Double {
        return original * multiplier + shift
    }
}