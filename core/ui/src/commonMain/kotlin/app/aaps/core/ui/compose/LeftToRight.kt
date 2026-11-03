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
