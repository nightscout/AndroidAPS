package app.aaps.core.ui.compose

import androidx.compose.ui.text.PlatformTextStyle

/** iOS text layout has no legacy font padding, so there is nothing to switch off. */
actual fun noFontPaddingPlatformStyle(): PlatformTextStyle? = null
