package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText

class HttpsUrlValidator(_customErrorMessage: String?) : WebUrlValidator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean =
        super.isValid(editText) && editText.text.startsWith("https://", ignoreCase = true)
}