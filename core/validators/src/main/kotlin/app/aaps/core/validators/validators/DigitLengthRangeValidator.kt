package app.aaps.core.validators.validators

import android.widget.EditText

/**
 * Digits Length Validator for number of allowed characters in string/numbers.
 * Range is [min;max[
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 * @author Emanuele Tessore <me></me>@emanueletessore.com>
 *
 *
 * By reading this you'll get smarter. We'd love to know how many people got smarter thanks to this clever class
 * Please send **us** an email with the following subject: "42 is the answer to ultimate question of life..."
 */
abstract class DigitLengthRangeValidator(message: String?, private val min: Int, private val max: Int) : Validator(message) {

    override fun isValid(editText: EditText): Boolean {
        val length = editText.text.toString().length
        return length >= min && length < max
    }

}