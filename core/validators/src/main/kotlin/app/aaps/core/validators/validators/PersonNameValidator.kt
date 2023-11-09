package app.aaps.core.validators.validators

class PersonNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}-]+")