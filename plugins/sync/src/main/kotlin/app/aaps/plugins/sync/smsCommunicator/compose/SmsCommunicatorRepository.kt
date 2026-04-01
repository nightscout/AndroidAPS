package app.aaps.plugins.sync.smsCommunicator.compose

import app.aaps.core.interfaces.smsCommunicator.Sms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
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
