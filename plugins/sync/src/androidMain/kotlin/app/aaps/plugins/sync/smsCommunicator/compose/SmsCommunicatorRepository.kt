package app.aaps.plugins.sync.smsCommunicator.compose

import app.aaps.core.interfaces.smsCommunicator.Sms
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@SingleIn(AppScope::class)
class SmsCommunicatorRepository @Inject constructor() {

    private val _messages = MutableStateFlow<List<Sms>>(emptyList())
    val messages: StateFlow<List<Sms>> = _messages

    fun updateMessages(messages: List<Sms>) {
        val sorted = messages.sortedBy { it.date }
        val messagesToShow = 40
        val start = max(0, sorted.size - messagesToShow)
        _messages.update { sorted.subList(start, sorted.size).map { Sms(it) } }
    }
}
