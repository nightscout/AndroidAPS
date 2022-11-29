package info.nightscout.rx.interfaces

interface LogElement {

    var name: String
    var defaultValue: Boolean
    var enabled: Boolean

    fun enable(enabled: Boolean)
    fun resetToDefault()
}