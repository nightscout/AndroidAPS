package info.nightscout.androidaps.utils.textValidator.validators

class AlphaValidator(message: String?) : RegexpValidator(message, "[A-z\u00C0-\u00ff \\./-\\?]*")