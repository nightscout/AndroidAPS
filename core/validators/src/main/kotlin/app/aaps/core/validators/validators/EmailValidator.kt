package app.aaps.core.validators.validators

import android.util.Patterns

/**
 * This validates an email using regexps.
 * Note that if an email passes the validation with this validator it doesn't mean it's a valid email - it means it's a valid email <storng>format
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
</storng> */
class EmailValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.EMAIL_ADDRESS)