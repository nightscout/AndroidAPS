package info.nightscout.androidaps.utils.textValidator.validators

import android.os.Build
import android.util.Patterns
import java.util.regex.Pattern

/**
 * It validates phone numbers.
 * Regexp was taken from the android source code.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class PhoneValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.PHONE)