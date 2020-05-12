package info.nightscout.androidaps.utils.textValidator.validators

class PersonFullNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}- ]+")