package app.aaps.pump.eopatch.compose

sealed class PatchEvent {
    data object Finish : PatchEvent()
    data class ShowCommError(val isForcedDiscard: Boolean) : PatchEvent()
    data class ShowBondedDialog(val dummy: Unit = Unit) : PatchEvent()
    data class ShowChangePatchDialog(val dummy: Unit = Unit) : PatchEvent()
    data class ShowDiscardDialog(val dummy: Unit = Unit) : PatchEvent()
    data object ShowForceResetDialog : PatchEvent()
}
