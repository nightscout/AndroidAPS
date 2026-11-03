package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Keeps [content] left to right also when the app runs in a right-to-left language.
 *
 * For a graph: time runs from left to right in every language (#5454). A mirrored graph puts the newest
 * values on the wrong side. Text inside still follows its own language.
 *
 * Do not put a dialog or a bottom sheet inside: those are normal screen content and must stay mirrored.
 */
@Composable
fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/**
 * [text] marked as left to right, for a number with a sign in a place without a Compose style: a
 * notification or a widget. Without this, Hebrew text in the same line turns the line around, and
 * "+0.4" shows as "0.4+".
 *
 * Only for what is shown on a screen. It is an invisible character, and one in an SMS makes the phone
 * send the whole message in a longer encoding.
 */
fun leftToRightText(text: String): String = "‎$text"
