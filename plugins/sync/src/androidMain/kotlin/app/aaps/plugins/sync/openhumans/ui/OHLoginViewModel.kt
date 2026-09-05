package app.aaps.plugins.sync.openhumans.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.plugins.sync.di.OpenHumansScope
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Contributed to OpenHumansScope, not AppScope: this class is internal to the module, and a
// contribution to AppScope would have to be nameable from `:app` where the root graph is generated.
@ContributesIntoMap(OpenHumansScope::class, binding = binding<ViewModel>())
@ViewModelKey
internal class OHLoginViewModel @Inject constructor(
    private val plugin: OpenHumansUploaderPlugin
) : ViewModel() {

    private val _state = MutableStateFlow(State.WELCOME)
    val state: StateFlow<State> = _state.asStateFlow()

    private var bearerToken = ""

    fun goToConsent() {
        _state.value = State.CONSENT
    }

    fun goBack() = when (_state.value) {
        State.CONSENT -> {
            _state.value = State.WELCOME
            true
        }

        State.CONFIRM -> {
            _state.value = State.CONSENT
            true
        }

        else          -> false
    }

    fun submitBearerToken(bearerToken: String) {
        if (_state.value == State.WELCOME || _state.value == State.CONSENT) {
            this.bearerToken = bearerToken
            _state.value = State.CONFIRM
        }
    }

    fun cancel() {
        _state.value = State.CONSENT
    }

    fun finish() {
        _state.value = State.FINISHING
        viewModelScope.launch {
            try {
                plugin.login(bearerToken)
                _state.value = State.DONE
            } catch (e: Exception) {
                _state.value = State.CONSENT
            }
        }
    }

    enum class State {
        WELCOME,
        CONSENT,
        CONFIRM,
        FINISHING,
        DONE
    }
}
