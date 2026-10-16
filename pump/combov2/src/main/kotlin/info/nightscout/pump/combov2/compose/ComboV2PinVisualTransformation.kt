package info.nightscout.pump.combov2.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Formats a Combo pairing PIN (10 digits) on screen as `xxx xxx xxxx`,
 * matching the layout the pump shows on its LCD.
 *
 * The underlying text field stores raw digits only — the ViewModel's
 * [ComboV2PairWizardViewModel.onPinTextChange] filters non-digits and
 * enforces the 10-digit maximum.
 */
class ComboV2PinVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = buildString {
            raw.forEachIndexed { index, c ->
                if (index == 3 || index == 6) append(' ')
                append(c)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 3 -> offset
                offset <= 6 -> offset + 1
                else        -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 3 -> offset
                offset <= 7 -> offset - 1
                else        -> offset - 2
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
