package info.nightscout.core.validators.validators

import android.widget.EditText

/**
 * Validator abstract class. To be used with FormEditText
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
abstract class Validator(var errorMessage: String?) {

    /**
     * Should check if the EditText is valid.
     *
     * @param the edittext under evaluation
     * @return true if the edittext is valid, false otherwise
     */
    abstract fun isValid(editText: EditText): Boolean

    fun hasErrorMessage(): Boolean {
        return errorMessage != null
    }

}