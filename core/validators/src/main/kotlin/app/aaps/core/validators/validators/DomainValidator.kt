package app.aaps.core.validators.validators

import android.util.Patterns

class DomainValidator(customErrorMessage: String?) : PatternValidator(customErrorMessage, Patterns.DOMAIN_NAME)