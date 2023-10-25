package app.aaps.core.validators.validators

import android.widget.EditText
import java.util.regex.Pattern

/**
 * Base class for regexp based validators.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 * @see DomainValidator
 *
 * @see EmailValidator
 *
 * @see IpAddressValidator
 *
 * @see PhoneValidator
 *
 * @see WebUrlValidator
 *
 * @see RegexpValidator
 */
open class PatternValidator(_customErrorMessage: String?, val pattern: Pattern) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return pattern.matcher(editText.text).matches()
    }

    fun isValid(text: String): Boolean {
        return pattern.matcher(text).matches()
    }
}