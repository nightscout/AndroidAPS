package app.aaps.core.validators.validators

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.EditText
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

class DateValidator(customErrorMessage: String?, format: String?) : Validator(customErrorMessage) {

    private val formats: Array<String> = if (format == null || TextUtils.isEmpty(format))
        arrayOf("DefaultDate", "DefaultTime", "DefaultDateTime")
    else
        format.split(";").toTypedArray()

    @SuppressLint("SimpleDateFormat")
    override fun isValid(editText: EditText): Boolean {
        if (TextUtils.isEmpty(editText.text)) return true
        val value = editText.text.toString()
        for (_format in formats) {
            val format: DateFormat = when {
                "DefaultDate".equals(_format, ignoreCase = true)     -> {
                    SimpleDateFormat.getDateInstance()
                }

                "DefaultTime".equals(_format, ignoreCase = true)     -> {
                    SimpleDateFormat.getTimeInstance()
                }

                "DefaultDateTime".equals(_format, ignoreCase = true) -> {
                    SimpleDateFormat.getDateTimeInstance()
                }

                else                                                 -> {
                    SimpleDateFormat(_format)
                }
            }
            var date = try {
                format.parse(value)
            } catch (_: ParseException) {
                return false
            }
            if (date != null) {
                return true
            }
        }
        return false
    }
}