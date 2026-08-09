package app.aaps.spike.cmp

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef

/**
 * The seam the whole `:core:ui` question turns on.
 *
 * 54 files in `:core:ui` call `androidx.compose.ui.res.stringResource`, which does not exist off
 * Android, and `PlusMinusEdit` reaches it indirectly through the same-package `stringResource(TextRef)`
 * resolver. So "can this file move to commonMain" is really "can the TextRef resolver be an
 * expect/actual". This is that shape, proved rather than assumed.
 *
 * Android keeps AAPT exactly as the app does today. iOS has no resource system here yet - and does
 * not need one for the spike, because the question is whether the SHAPE compiles for Kotlin/Native,
 * not whether iOS can render Czech.
 */
@Composable
expect fun stringResource(ref: TextRef): String
