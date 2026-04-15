package app.aaps.plugins.automation.elements

class InputButton() {

    var text: String? = null
    var runnable: Runnable? = null

    constructor(text: String, runnable: Runnable) : this() {
        this.text = text
        this.runnable = runnable
    }
}
