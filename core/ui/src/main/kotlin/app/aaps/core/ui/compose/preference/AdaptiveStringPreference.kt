/*
 * Adaptive String Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import app.aaps.core.ui.UiStrings
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.stringResource

/**
 * Composable string preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses stringKey.title
 * @param summary Optional summary override. If null, uses stringKey.summary
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveStringPreferencePreview
 */
@Composable
fun AdaptiveStringPreferenceItem(
    stringKey: StringPreferenceKey,
    title: TextRef? = null,
    summary: TextRef? = null,
    isPassword: Boolean = false,
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: stringKey.title
    val effectiveSummary = summary ?: stringKey.summary

    val visibility = calculatePreferenceVisibility(
        preferenceKey = stringKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val state = rememberPreferenceStringState(stringKey)
    val value = state.value
    val validator = stringKey.validator
    val isSecure = isPassword || stringKey.isPassword || stringKey.isPin

    // Get dialog summary from key
    val dialogSummary = if (effectiveSummary != null) stringResource(effectiveSummary) else null

    TextFieldPreference(
        state = state,
        title = { Text(stringResource(effectiveTitle)) },
        textToValue = { text ->
            val result = validator.validate(text)
            if (result.isValid) text else null
        },
        enabled = visibility.enabled,
        summary = when {
            isSecure && value.isNotEmpty() -> {
                { Text("••••••••") }
            }

            isSecure && value.isEmpty()    -> {
                val notSetResId = if (stringKey.isPin) UiStrings.pin_not_set else UiStrings.password_not_set
                { Text(stringResource(effectiveSummary ?: notSetResId)) }
            }

            value.isNotEmpty()             -> {
                { Text(value) }
            }

            effectiveSummary != null       -> {
                { Text(stringResource(effectiveSummary)) }
            }

            else                           -> null
        },
        dialogSummary = dialogSummary,
        textField = if (validator != StringValidator.NONE) {
            { textFieldValue, onValueChange, onOk ->
                ValidatedTextField(
                    value = textFieldValue,
                    onValueChange = onValueChange,
                    onOk = onOk,
                    validator = validator
                )
            }
        } else {
            TextFieldPreferenceDefaults.TextField
        }
    )
}

/**
 * TextField with real-time validation error display.
 */
@Composable
private fun ValidatedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    validator: StringValidator
) {
    // Only re-validate when the text actually changes, not on every recomposition
    val errorMessage = remember(value.text) {
        val result = validator.validate(value.text)
        if (!result.isValid) result.errorMessage else null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        keyboardActions = KeyboardActions { onOk() },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } }
    )
}
