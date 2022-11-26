package info.nightscout.core.validators.validators

class PersonNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}-]+")