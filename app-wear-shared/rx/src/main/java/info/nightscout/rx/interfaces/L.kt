package info.nightscout.rx.interfaces

interface L {
    fun resetToDefaults()
    fun findByName(name: String): LogElement
    fun getLogElements(): List<LogElement>
}