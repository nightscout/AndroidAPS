package info.nightscout.androidaps.utils.textValidator.validators

import android.util.Patterns
import android.widget.EditText

class MultiPhoneValidator(val _customErrorMessage: String?) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        val substrings = editText.text.split(";").toTypedArray()
        for (number in substrings) {
            if (!PatternValidator(_customErrorMessage, Patterns.PHONE).isValid(number))
                return false
        }
        return substrings.isNotEmpty()
    }
}