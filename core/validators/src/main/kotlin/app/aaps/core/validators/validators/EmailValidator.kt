package app.aaps.core.validators.validators

import android.util.Patterns

/**
 * This validates an email using regexps.
 * Note that if an email passes the validation with this validator it doesn't mean it's a valid email - it means it's a valid email <strong>format
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
</storng> */
class EmailValidator(customErrorMessage: String?) : PatternValidator(customErrorMessage, Patterns.EMAIL_ADDRESS)