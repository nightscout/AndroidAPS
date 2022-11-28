package info.nightscout.core.validators.validators

import android.text.TextUtils
import android.widget.EditText

/**
 * A simple validator that validates the field only if the value is the same as another one.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class SameValueValidator(private val otherEditText: EditText, errorMessage: String?) : Validator(errorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return TextUtils.equals(editText.text, otherEditText.text)
    }

}