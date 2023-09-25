package app.aaps.core.validators.validators

import java.util.Arrays

/**
 * Abstract class for a multivalidator.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 * @see AndValidator
 *
 * @see OrValidator
 */
abstract class MultiValidator : Validator {

    protected val validators: MutableList<Validator>

    constructor(message: String?, vararg validators: Validator?) : super(message) {
        this.validators = ArrayList<Validator>(Arrays.asList(*validators))
    }

    constructor(message: String?) : super(message) {
        validators = ArrayList()
    }

    fun enqueue(newValidator: Validator) {
        validators.add(newValidator)
    }
}