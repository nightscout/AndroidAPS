package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.compositionLocalOf
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.ui.compose.ComposeScreenContent

/**
 * CompositionLocal for providing PreferenceVisibilityContext to preference composables.
 * Used by AdaptivePreferenceList and PreferenceContentExtensions to evaluate visibility conditions.
 */
val LocalVisibilityContext = compositionLocalOf<PreferenceVisibilityContext?> { null }

/**
 * CompositionLocal for password verification function.
 * Used by password preference dialogs to verify entered passwords.
 * Signature: (enteredPassword: String, storedHash: String) -> Boolean
 */
val LocalCheckPassword = compositionLocalOf<((String, String) -> Boolean)?> { null }

/**
 * CompositionLocal for password hashing function.
 * Used by password preference dialogs to hash passwords before storing.
 * Signature: (password: String) -> String
 */
val LocalHashPassword = compositionLocalOf<((String) -> String)?> { null }

/**
 * CompositionLocal for highlighting a specific preference key.
 * Used when navigating from search to highlight the found preference.
 */
val LocalHighlightKey = compositionLocalOf<String?> { null }

/**
 * CompositionLocal for navigating to an inline Compose screen from a preference.
 * Used by IntentPreferenceKey with composeScreen attached via withCompose().
 * The lambda receives a composable content that takes an onBack callback.
 */
val LocalNavigateToCompose = compositionLocalOf<((ComposeScreenContent) -> Unit)?> { null }
