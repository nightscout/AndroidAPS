package app.aaps.core.validators.validators

import android.util.Patterns

class DomainValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.DOMAIN_NAME)