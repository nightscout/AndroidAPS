/*
 * Adaptive Preference Renderer for Jetpack Compose
 * Provides generic rendering for any PreferenceKey type
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.StringKeyWithEntriesProvider
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.ui.compose.ComposeScreenContent

/**
 * Renders a preference based on its PreferenceKey type and preferenceType.
 * Automatically selects the appropriate composable.
 *
 * For LIST types, loads entries from resources using entriesResId/entryValuesResId.
 * For URL/ACTIVITY types on IntentPreferenceKey, requires additional parameters.
 *
 * @param key The PreferenceKey to render
 * @param onIntentClick Optional click handler for IntentPreferenceKey with CLICK type
 * @param intentUrl Optional URL for IntentPreferenceKey with URL type
 * @param intentActivityClass Optional Activity class for IntentPreferenceKey with ACTIVITY type
 */
@Composable
fun AdaptivePreferenceItem(
    key: PreferenceKey,
    visibilityContext: PreferenceVisibilityContext? = null,
    onIntentClick: (() -> Unit)? = null,
    intentUrl: String? = null,
    intentActivityClass: Class<*>? = null
) {
    when (key) {
        is BooleanPreferenceKey         -> {
            AdaptiveSwitchPreferenceItem(

                booleanKey = key,
                visibilityContext = visibilityContext
            )
        }

        is IntPreferenceKey             -> {
            when (key.preferenceType) {
                PreferenceType.LIST       -> {
                    // Check for runtime-resolved entries first (from withEntries)
                    val resolved = key.resolvedEntries
                    if (resolved != null) {
                        AdaptiveListIntPreferenceItem(

                            intKey = key,
                            entries = resolved.values.toList(),
                            entryValues = resolved.keys.toList(),
                            visibilityContext = visibilityContext
                        )
                    } else if (key.entries.isNotEmpty()) {
                        AdaptiveListIntPreferenceItem(

                            intKey = key,
                            entries = key.entries.values.map { stringResource(it) },
                            entryValues = key.entries.keys.toList(),
                            visibilityContext = visibilityContext
                        )
                    }
                }

                PreferenceType.TEXT_FIELD -> {
                    AdaptiveIntPreferenceItem(

                        intKey = key,
                        visibilityContext = visibilityContext
                    )
                }

                else                      -> {
                    // Default to text field for unsupported types
                    AdaptiveIntPreferenceItem(

                        intKey = key,
                        visibilityContext = visibilityContext
                    )
                }
            }
        }

        is DoublePreferenceKey          -> {
            AdaptiveDoublePreferenceItem(

                doubleKey = key,
                visibilityContext = visibilityContext
            )
        }

        is StringKeyWithEntriesProvider -> {
            // Handle context-dependent entries provider
            val context = LocalContext.current
            val entries = remember(key) { key.entriesProvider(context) }
            val emptyMessageResId = key.emptyEntriesMessageResId

            if (entries.isNotEmpty()) {
                AdaptiveStringListPreferenceItem(
                    stringKey = key,
                    entries = entries,
                    visibilityContext = visibilityContext
                )
            } else if (emptyMessageResId != null) {
                // Show disabled preference with empty message
                Preference(
                    title = { Text(stringResource(key.titleResId)) },
                    summary = { Text(stringResource(emptyMessageResId)) },
                    enabled = false
                )
            }
        }

        is StringPreferenceKey          -> {
            // Handle password/PIN fields specially
            if (key.isPassword || key.isPin) {
                val checkPassword = LocalCheckPassword.current
                val hashPassword = LocalHashPassword.current
                if (checkPassword != null && hashPassword != null) {
                    // Special handling for master password (requires current password first)
                    if (key == StringKey.ProtectionMasterPassword) {
                        AdaptiveMasterPasswordPreferenceItem(

                            checkPassword = checkPassword,
                            hashPassword = hashPassword
                        )
                    } else {
                        AdaptivePasswordPreferenceItem(

                            stringKey = key,
                            hashPassword = hashPassword,
                            visibilityContext = visibilityContext
                        )
                    }
                }
            } else {
                when (key.preferenceType) {
                    PreferenceType.LIST       -> {
                        // Check for runtime-resolved entries first (from withEntries)
                        val entriesMap = key.resolvedEntries
                            ?: key.entries.takeIf { it.isNotEmpty() }?.mapValues { (_, resId) -> stringResource(resId) }

                        if (entriesMap != null) {
                            AdaptiveStringListPreferenceItem(

                                stringKey = key,
                                entries = entriesMap,
                                visibilityContext = visibilityContext
                            )
                        }
                    }

                    PreferenceType.TEXT_FIELD -> {
                        AdaptiveStringPreferenceItem(

                            stringKey = key,
                            visibilityContext = visibilityContext
                        )
                    }

                    else                      -> {
                        AdaptiveStringPreferenceItem(

                            stringKey = key,
                            visibilityContext = visibilityContext
                        )
                    }
                }
            }
        }

        is UnitDoublePreferenceKey      -> {
            AdaptiveUnitDoublePreferenceItem(
                unitKey = key,
                visibilityContext = visibilityContext
            )
        }

        is IntentPreferenceKey          -> {
            // Priority: 1) runtime click  2) compose screen  3) activity  4) url
            val resolvedClick = key.onClick ?: onIntentClick
            val resolvedCompose = key.composeScreen as? ComposeScreenContent
            val onNavigateToCompose = LocalNavigateToCompose.current
            val resolvedActivity = key.runtimeActivityClass ?: intentActivityClass ?: key.activityClass
            val resolvedUrl = key.runtimeUrl ?: intentUrl ?: key.urlResId?.let { stringResource(it) }

            when {
                resolvedClick != null                                   -> {
                    AdaptiveIntentPreferenceItem(

                        intentKey = key,
                        onClick = resolvedClick,
                        visibilityContext = visibilityContext
                    )
                }

                resolvedCompose != null && onNavigateToCompose != null  -> {
                    AdaptiveComposeScreenPreferenceItem(
                        intentKey = key,
                        composeScreen = resolvedCompose,
                        onNavigate = onNavigateToCompose,
                        visibilityContext = visibilityContext
                    )
                }

                resolvedActivity != null                                -> {
                    AdaptiveDynamicActivityPreferenceItem(

                        intentKey = key,
                        activityClass = resolvedActivity,
                        visibilityContext = visibilityContext
                    )
                }

                resolvedUrl != null                         -> {
                    AdaptiveUrlPreferenceItem(

                        intentKey = key,
                        url = resolvedUrl,
                        visibilityContext = visibilityContext
                    )
                }
            }
        }
    }
}

