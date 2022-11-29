package info.nightscout.core.validators.validators

class PersonFullNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}- ]+")