package info.nightscout.core.validators.validators

import android.util.Patterns

/**
 * It validates phone numbers.
 * Regexp was taken from the android source code.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class PhoneValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.PHONE)