package app.aaps.interfaces.logging

interface L {

    fun resetToDefaults()
    fun findByName(name: String): LogElement
    fun getLogElements(): List<LogElement>
}