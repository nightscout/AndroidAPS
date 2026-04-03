/*
 * Adaptive String Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator

/**
 * Composable string preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses stringKey.titleResId
 * @param summaryResId Optional summary resource ID. If null, uses stringKey.summaryResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveStringPreferenceItem(
    stringKey: StringPreferenceKey,
    titleResId: Int = 0,
    summaryResId: Int? = null,
    isPassword: Boolean = false,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else stringKey.titleResId
    val effectiveSummaryResId = summaryResId ?: stringKey.summaryResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

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
    val dialogSummary = if (effectiveSummaryResId != null) stringResource(effectiveSummaryResId) else null

    TextFieldPreference(
        state = state,
        title = { Text(stringResource(effectiveTitleResId)) },
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
                val notSetResId = if (stringKey.isPin) app.aaps.core.ui.R.string.pin_not_set else app.aaps.core.ui.R.string.password_not_set
                { Text(stringResource(effectiveSummaryResId ?: notSetResId)) }
            }

            value.isNotEmpty()             -> {
                { Text(value) }
            }

            effectiveSummaryResId != null  -> {
                { Text(stringResource(effectiveSummaryResId)) }
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

@Preview(showBackground = true)
@Composable
private fun AdaptiveStringPreferencePreview() {
    PreviewTheme {
        AdaptiveStringPreferenceItem(
            stringKey = StringKey.GeneralPatientName
        )
    }
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
