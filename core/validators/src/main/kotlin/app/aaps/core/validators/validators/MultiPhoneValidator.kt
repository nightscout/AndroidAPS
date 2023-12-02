package app.aaps.core.validators.validators

import android.util.Patterns
import android.widget.EditText

class MultiPhoneValidator(val _customErrorMessage: String?) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        val substrings = editText.text.split(";").toTypedArray()
        val knownNumbers = HashSet<String>()
        for (number in substrings) {
            if (!PatternValidator(_customErrorMessage, Patterns.PHONE).isValid(number))
                return false
            if (knownNumbers.contains(number))
                return false
            knownNumbers.add(number)
        }
        return substrings.isNotEmpty()
    }
}