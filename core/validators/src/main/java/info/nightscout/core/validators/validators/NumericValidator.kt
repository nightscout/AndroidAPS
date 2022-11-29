package info.nightscout.core.validators.validators

import android.text.TextUtils
import android.widget.EditText

/**
 * A validator that returns true only if the input field contains only numbers.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class NumericValidator(_customErrorMessage: String?) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return TextUtils.isDigitsOnly(editText.text)
    }
}