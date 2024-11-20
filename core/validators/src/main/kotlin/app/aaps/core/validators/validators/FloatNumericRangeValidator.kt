package app.aaps.core.validators.validators

import android.widget.EditText
import app.aaps.core.interfaces.utils.SafeParse

/**
 * A validator that returns true only if the input field contains only numbers
 * and the number is within the given range.
 *
 * @author Said Tahsin Dane <tasomaniac></tasomaniac>@gmail.com>
 */
class FloatNumericRangeValidator(customErrorMessage: String?, private val floatMin: Float, private val floatMax: Float) : Validator(customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return try {
            val value = SafeParse.stringToFloat(editText.text.toString())
            value in floatMin..floatMax
        } catch (_: NumberFormatException) {
            false
        }
    }

}