package app.aaps.core.ui.compose

import androidx.compose.ui.text.PlatformTextStyle

actual fun noFontPaddingPlatformStyle(): PlatformTextStyle? = PlatformTextStyle(includeFontPadding = false)
