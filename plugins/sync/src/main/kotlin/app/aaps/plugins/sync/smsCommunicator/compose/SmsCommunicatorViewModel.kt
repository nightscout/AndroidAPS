package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.utils.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SmsItem(
    val time: String,
    val phoneNumber: String,
    val text: String,
    val isReceived: Boolean,
    val isSent: Boolean,
    val isProcessed: Boolean,
    val isIgnored: Boolean
)

@Immutable
data class SmsCommunicatorUiState(
    val messages: List<SmsItem> = emptyList()
)

@HiltViewModel
class SmsCommunicatorViewModel @Inject constructor(
    private val repository: SmsCommunicatorRepository,
    private val dateUtil: DateUtil
) : ViewModel() {

    val uiState: StateFlow<SmsCommunicatorUiState>
        field = MutableStateFlow(SmsCommunicatorUiState())

    init {
        viewModelScope.launch {
            repository.messages.collect { smsList ->
                val items = smsList.map { sms ->
                    SmsItem(
                        time = dateUtil.timeString(sms.date),
                        phoneNumber = sms.phoneNumber,
                        text = sms.text,
                        isReceived = sms.received,
                        isSent = sms.sent,
                        isProcessed = sms.processed,
                        isIgnored = sms.ignored
                    )
                }
                uiState.update { it.copy(messages = items) }
            }
        }
    }
}
