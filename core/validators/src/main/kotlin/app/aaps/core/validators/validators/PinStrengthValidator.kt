package app.aaps.core.validators.validators

import android.widget.EditText

class PinStrengthValidator(val _customErrorMessage: String?) : Validator(_customErrorMessage) {

    val regex = "[0-9]{3,6}".toRegex()

    override fun isValid(editText: EditText): Boolean {
        return try {
            val value = editText.text.toString()
            if (!regex.matches(value))
                return false

            var last = ' '
            var rising = true
            var falling = true
            var same = true
            value.forEachIndexed { i, c ->
                if (i > 0) {
                    if (last != c) {
                        same = false
                    }

                    if (last.code + 1 != c.code) {
                        falling = false
                    }

                    if (last.code != c.code + 1) {
                        rising = false
                    }
                }
                last = c
            }

            !rising && !falling && !same
        } catch (e: NumberFormatException) {
            false
        }
    }
}