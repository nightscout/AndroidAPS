package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Composable

/** The desktop links no pump driver, so no Aaruy screen is ever shown there. */
@Composable
internal actual fun KeepScreenOn() = Unit
