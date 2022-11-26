package info.nightscout.core.validators.validators

import android.widget.EditText

/**
 * The or validator checks if one of passed validators is returning true.<br></br>
 * Note: the message that will be shown is the one passed to the Constructor
 *
 * @author Andrea B.
 */
class OrValidator(message: String?, vararg validators: Validator?) : MultiValidator(message, *validators) {

    override fun isValid(editText: EditText): Boolean {
        for (v in validators) {
            if (v.isValid(editText)) {
                return true // Remember :) We're acting like an || operator.
            }
        }
        return false
    }
}