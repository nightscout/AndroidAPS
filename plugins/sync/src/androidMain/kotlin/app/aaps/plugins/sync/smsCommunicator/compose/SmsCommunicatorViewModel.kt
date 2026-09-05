package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class SmsCommunicatorViewModel @Inject constructor(
    private val repository: SmsCommunicatorRepository,
    private val dateUtil: DateUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsCommunicatorUiState())
    val uiState: StateFlow<SmsCommunicatorUiState> = _uiState.asStateFlow()

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
                _uiState.update { it.copy(messages = items) }
            }
        }
    }
}
