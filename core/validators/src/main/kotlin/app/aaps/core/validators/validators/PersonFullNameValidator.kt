package app.aaps.core.validators.validators

class PersonFullNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}- ]+")