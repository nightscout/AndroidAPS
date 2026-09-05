package app.aaps.plugins.automation.elements

class InputPercent() {

    var value: Double = 100.0

    constructor(value: Double) : this() {
        this.value = value
    }

    companion object {

        const val MIN = 50.0
        const val MAX = 130.0
    }
}
