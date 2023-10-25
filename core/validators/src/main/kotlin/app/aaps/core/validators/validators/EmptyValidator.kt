package app.aaps.core.validators.validators

import android.text.TextUtils
import android.widget.EditText

/**
 * A simple validator that validates the field only if the field is not empty.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class EmptyValidator(message: String?) : Validator(message) {

    override fun isValid(editText: EditText): Boolean {
        return TextUtils.getTrimmedLength(editText.text) > 0
    }
}