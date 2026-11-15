package app.aaps.pump.carelevo.common.model

/** Marker for observable state values exposed by ViewModels/singletons (vs one-shot [Event]s). */
interface State

/** Generic busy indicator for screens that show a blocking progress overlay during BLE ops. */
sealed class UiState : State {
    data object Idle : UiState()
    data object Loading : UiState()
}

/**
 * Coarse patch lifecycle derived in `CarelevoPatch.resolvePatchState` from the persisted patch
 * record ("booted" = activation completed) + live BLE link state. Drives which overview/wizard
 * surface is shown.
 */
sealed interface PatchState {

    data object NotConnectedNotBooting : PatchState
    data object NotConnectedBooted : PatchState
    data object ConnectedNoBooted : PatchState
    data object ConnectedBooted : PatchState

}

/** Delivery run-state of the pump as last reported by the patch (idle/delivering/suspended). */
sealed interface PumpState {

    data object Idle : PumpState
    data object Start : PumpState
    data object Stop : PumpState

}