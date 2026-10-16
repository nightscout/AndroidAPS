package app.aaps.plugins.automation.elements

class InputInsulin() {

    var value = 0.0

    constructor(another: InputInsulin) : this() {
        value = another.value
    }
}
