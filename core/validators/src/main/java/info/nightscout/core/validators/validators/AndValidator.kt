package info.nightscout.core.validators.validators

import android.widget.EditText

/**
 * The AND validator checks if all of the passed validators is returning true.<br></br>
 * Note: the message that will be shown is the one of the first failing validator
 */
class AndValidator : MultiValidator {

    constructor(vararg validators: Validator?) : super(null, *validators)
    constructor() : super(null)

    override fun isValid(editText: EditText): Boolean {
        for (v in validators) {
            if (!v.isValid(editText)) {
                errorMessage = v.errorMessage
                return false
            }
        }
        return true
    }
}