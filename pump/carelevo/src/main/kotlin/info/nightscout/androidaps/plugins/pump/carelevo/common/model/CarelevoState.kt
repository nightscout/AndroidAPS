package info.nightscout.androidaps.plugins.pump.carelevo.common.model

interface State {
}

sealed class UiState : State {
    data object Idle : UiState()
    data object Loading : UiState()
}

sealed interface PatchState {

    data object NotConnectedNotBooting : PatchState
    data object NotConnectedBooted : PatchState
    data object ConnectedNoBooted : PatchState
    data object ConnectedBooted : PatchState

}

sealed interface PumpState {

    data object Idle : PumpState
    data object Start : PumpState
    data object Stop : PumpState

}