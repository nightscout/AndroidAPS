package app.aaps.core.interfaces.logging

interface L {

    fun resetToDefaults()
    fun findByName(name: String): LogElement
    fun logElements(): List<LogElement>
}