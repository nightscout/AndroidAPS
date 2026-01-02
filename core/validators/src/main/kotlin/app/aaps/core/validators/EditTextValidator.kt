package app.aaps.core.validators

import android.content.Context
import app.aaps.core.validators.validators.Validator

/**
 * Interface for encapsulating validation of an EditText control
 */
interface EditTextValidator {

    /**
     * Add a validator to this FormEditText. The validator will be added in the
     * queue of the current validators.
     *
     * @throws IllegalArgumentException if the validator is null
     */
    @Throws(IllegalArgumentException::class)
    fun addValidator(theValidator: Validator)

    fun isEmptyAllowed(): Boolean

    /**
     * Resets the [Validator]s
     */
    fun resetValidators(context: Context)

    /**
     * Calling *testValidity()* will cause the EditText to go through
     * customValidators and call {#Validator.isValid(EditText)}
     * Same as [.testValidity] with first parameter true
     *
     * @return true if the validity passes false otherwise.
     */
    fun testValidity(): Boolean

    /**
     * Calling *testValidity()* will cause the EditText to go through
     * customValidators and call {#Validator.isValid(EditText)}
     *
     * @param showUIError determines if this call should show the UI error.
     * @return true if the validity passes false otherwise.
     */
    fun testValidity(showUIError: Boolean): Boolean
    fun showUIError()

    companion object {

        const val TEST_REGEXP = 0
        const val TEST_NUMERIC = 1
        const val TEST_ALPHA = 2
        const val TEST_ALPHANUMERIC = 3
        const val TEST_EMAIL = 4
        const val TEST_CREDITCARD = 5
        const val TEST_PHONE = 6
        const val TEST_DOMAINNAME = 7
        const val TEST_IPADDRESS = 8
        const val TEST_WEBURL = 9
        const val TEST_NOCHECK = 10
        const val TEST_CUSTOM = 11
        const val TEST_PERSONNAME = 12
        const val TEST_PERSONFULLNAME = 13
        const val TEST_DATE = 14
        const val TEST_NUMERIC_RANGE = 15
        const val TEST_FLOAT_NUMERIC_RANGE = 16
        const val TEST_HTTPS_URL = 17
        const val TEST_MIN_LENGTH = 18
        const val TEST_MULTI_PHONE = 19
        const val TEST_PIN_STRENGTH = 20
        const val TEST_BG_RANGE = 21
    }
}