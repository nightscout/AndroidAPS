package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText

/**
 * A validator that returns true only if the input field contains only numbers
 * and the number is within the given range.
 *
 * @author Said Tahsin Dane <tasomaniac></tasomaniac>@gmail.com>
 */
class FloatNumericRangeValidator(_customErrorMessage: String?, private val floatMin: Float, private val floatMax: Float) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return try {
            val value = editText.text.toString().toFloat()
            value in floatMin..floatMax
        } catch (e: NumberFormatException) {
            false
        }
    }

}