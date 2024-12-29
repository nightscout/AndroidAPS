package app.aaps.core.validators.validators

import android.util.Patterns

/**
 * Validates a web url in the format:
 * scheme + authority + path
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
open class WebUrlValidator(customErrorMessage: String?) : PatternValidator(customErrorMessage, Patterns.WEB_URL)