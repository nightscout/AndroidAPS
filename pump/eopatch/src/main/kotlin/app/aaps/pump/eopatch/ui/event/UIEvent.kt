package app.aaps.pump.eopatch.ui.event

open class UIEvent<out T>(private val content: T) {

    var value: Any? = null
    fun peekContent(): T = content
}

