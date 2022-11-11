package info.nightscout.androidaps.utils.textValidator.validators

class PersonNameValidator(message: String?) : RegexpValidator(message, "[\\p{L}-]+")