package app.aaps.core.ui.compose

import androidx.compose.ui.text.PlatformTextStyle

/**
 * The platform text style that turns off the legacy font padding, or null where there is none.
 *
 * `includeFontPadding` is an Android text-layout quirk: the platform reserves extra space above the
 * glyphs from the font ascent, which stops text from centering in its line box. No other platform
 * has it, and `PlatformTextStyle` only accepts the flag on Android, so this is the one line of
 * [Typography.withoutFontPadding] that cannot be shared.
 */
expect fun noFontPaddingPlatformStyle(): PlatformTextStyle?
