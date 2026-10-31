package app.aaps.core.interfaces.logging

interface LogElement {

    var name: String
    var defaultValue: Boolean
    var enabled: Boolean

    fun enable(enabled: Boolean)
    fun resetToDefault()
}