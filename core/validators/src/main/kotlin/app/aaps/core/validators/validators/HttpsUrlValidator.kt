package app.aaps.core.validators.validators

import android.widget.EditText

class HttpsUrlValidator(customErrorMessage: String?) : WebUrlValidator(customErrorMessage) {

    override fun isValid(editText: EditText): Boolean =
        super.isValid(editText) && editText.text.startsWith("https://", ignoreCase = true)
}