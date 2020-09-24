package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText

class MinDigitLengthValidator(message: String?, private val min: Int) : Validator(message) {

    override fun isValid(editText: EditText): Boolean {
        val length = editText.text.toString().length
        return length >= min
    }

}