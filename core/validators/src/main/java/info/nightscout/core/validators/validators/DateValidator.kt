package info.nightscout.core.validators.validators

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.EditText
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

class DateValidator(_customErrorMessage: String?, _format: String?) : Validator(_customErrorMessage) {
    private val formats: Array<String> = if (_format == null || TextUtils.isEmpty(_format))
        arrayOf("DefaultDate", "DefaultTime", "DefaultDateTime")
    else
        _format.split(";").toTypedArray()

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
            var date: Date?
            date = try {
                format.parse(value)
            } catch (e: ParseException) {
                return false
            }
            if (date != null) {
                return true
            }
        }
        return false
    }
}