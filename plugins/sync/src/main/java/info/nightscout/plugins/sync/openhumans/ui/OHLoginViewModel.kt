package info.nightscout.plugins.sync.openhumans.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import info.nightscout.plugins.sync.openhumans.OpenHumansUploaderPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class OHLoginViewModel @Inject constructor(
    private val plugin: OpenHumansUploaderPlugin
) : ViewModel(), CoroutineScope by MainScope() {

    private val _state = MutableLiveData(State.WELCOME)
    val state = _state as LiveData<State>

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
        launch {
            try {
                plugin.login(bearerToken)
                _state.value = State.DONE
            } catch (e: Exception) {
                _state.value = State.CONSENT
            }
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    enum class State {
        WELCOME,
        CONSENT,
        CONFIRM,
        FINISHING,
        DONE
    }
}