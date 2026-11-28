package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.ui.compose.AapsSpacing

/**
 * An outlined text field for the food screens, smaller than the Material one: the text is one step
 * smaller and the inner margins are 12 dp instead of 16 dp. The field is as high as a touch target
 * (48 dp) instead of 56 dp. On a 360 dp wide phone three fields fit on one row, and a value such as
 * "Feculent" is not cut.
 *
 * With [singleLine] false the field grows while the user writes (the notes).
 * The label stays on one line, and is cut with "…" when the field is too narrow for it.
 * Material 3 has no parameter for these margins, so the field is built from the same parts as
 * `OutlinedTextField`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError  -> colors.errorTextColor
        focused  -> colors.focusedTextColor
        else     -> colors.unfocusedTextColor
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        // The label sits on the top border, half above it: OutlinedTextField keeps room for it in
        // the same way, or the label is cut by what is above the field.
        modifier = modifier
            .padding(top = AapsSpacing.medium)
            .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            // The label is drawn in bodyLarge when it is inside the field. It takes the size of the
            // text here, or it would be bigger than the value it names.
            MaterialTheme(typography = MaterialTheme.typography.copy(bodyLarge = MaterialTheme.typography.bodyMedium)) {
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = trailingIcon,
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium),
                )
            }
        }
    )
}
