package info.nightscout.core.validators.validators

import android.widget.EditText

/**
 * It's a validator that applies the "NOT" logical operator to the validator passed in the constructor.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class NotValidator(errorMessage: String?, private val v: Validator) : Validator(errorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return !v.isValid(editText)
    }
}