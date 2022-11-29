package info.nightscout.core.validators.validators

class AlphaValidator(message: String?) : RegexpValidator(message, "[A-z\u00C0-\u00ff \\./-\\?]*")