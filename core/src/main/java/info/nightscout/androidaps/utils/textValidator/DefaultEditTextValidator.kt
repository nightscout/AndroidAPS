package info.nightscout.androidaps.utils.textValidator

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.textValidator.validators.*

@Suppress("SpellCheckingInspection")
class DefaultEditTextValidator : EditTextValidator {
    private var mValidator: MultiValidator? = null
    private var testErrorString: String? = null
    private var emptyAllowed = false
    private lateinit var editTextView: EditText
    private var tw: TextWatcher? = null
    private var defaultEmptyErrorString: String? = null

    private var testType: Int
    private var classType: String? = null
    private var customRegexp: String? = null
    private var customFormat: String? = null
    private var emptyErrorStringActual: String? = null
    private var emptyErrorStringDef: String? = null
    private var minLength = 0
    private var minNumber = 0
    private var maxNumber = 0
    private var floatminNumber = 0f
    private var floatmaxNumber = 0f

    @Suppress("unused")
    constructor(editTextView: EditText, context: Context) {
        testType = EditTextValidator.TEST_NOCHECK
        setEditText(editTextView)
        resetValidators(context)
    }

    constructor(editTextView: EditText, parameters: Parameters, context: Context) {
        emptyAllowed = parameters.emptyAllowed
        testType = parameters.testType
        testErrorString = parameters.testErrorString
        classType = parameters.classType
        customRegexp = parameters.customRegexp
        emptyErrorStringDef = parameters.emptyErrorStringDef
        customFormat = parameters.customFormat
        minLength = parameters.minLength
        minNumber = parameters.minNumber
        maxNumber = parameters.maxNumber
        floatminNumber = parameters.floatminNumber
        floatmaxNumber = parameters.floatmaxNumber

        setEditText(editTextView)
        resetValidators(context)
    }

    @Throws(IllegalArgumentException::class)
    override fun addValidator(theValidator: Validator) {
        mValidator?.enqueue(theValidator)
    }

    private fun setEditText(editText: EditText) {
        //editTextView?.removeTextChangedListener(textWatcher)
        editTextView = editText
        editText.addTextChangedListener(textWatcher)
    }

    override fun getTextWatcher(): TextWatcher {
        if (tw == null) {
            tw = object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    testValidity()
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (!TextUtils.isEmpty(s) && isErrorShown) {
                        try {
                            val textInputLayout = editTextView.parent as TextInputLayout
                            textInputLayout.isErrorEnabled = false
                        } catch (e: Throwable) {
                            editTextView.error = null
                        }
                    }
                }
            }
        }
        return tw!!
    }

    override fun isEmptyAllowed(): Boolean {
        return emptyAllowed
    }

    override fun resetValidators(context: Context) {
        // its possible the context may have changed so re-get the defaultEmptyErrorString
        defaultEmptyErrorString = context.getString(R.string.error_field_must_not_be_empty)
        setEmptyErrorString(emptyErrorStringDef)
        mValidator = AndValidator()
        val toAdd: Validator
        when (testType) {
            EditTextValidator.TEST_NOCHECK             -> toAdd = DummyValidator()
            EditTextValidator.TEST_ALPHA               -> toAdd = AlphaValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_standard_letters_are_allowed) else testErrorString)
            EditTextValidator.TEST_ALPHANUMERIC        -> toAdd = AlphaNumericValidator(
                if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_this_field_cannot_contain_special_character) else testErrorString)
            EditTextValidator.TEST_NUMERIC             -> toAdd = NumericValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_numeric_digits_allowed) else testErrorString)
            EditTextValidator.TEST_NUMERIC_RANGE       -> toAdd = NumericRangeValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_numeric_digits_range_allowed, minNumber.toString(), maxNumber.toString()) else testErrorString, minNumber, maxNumber)
            EditTextValidator.TEST_FLOAT_NUMERIC_RANGE -> toAdd = FloatNumericRangeValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_numeric_digits_range_allowed, floatminNumber.toString(), floatmaxNumber.toString()) else testErrorString, floatminNumber, floatmaxNumber)
            EditTextValidator.TEST_REGEXP              -> toAdd = RegexpValidator(testErrorString, customRegexp ?: "")
            EditTextValidator.TEST_CREDITCARD          -> toAdd = CreditCardValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_creditcard_number_not_valid) else testErrorString)
            EditTextValidator.TEST_EMAIL               -> toAdd = EmailValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_email_address_not_valid) else testErrorString)
            EditTextValidator.TEST_PHONE               -> toAdd = PhoneValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_phone_not_valid) else testErrorString)
            EditTextValidator.TEST_MULTI_PHONE         -> toAdd = MultiPhoneValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_phone_not_valid) else testErrorString)
            EditTextValidator.TEST_PIN_STRENGTH        -> toAdd = PinStrengthValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_pin_not_valid) else testErrorString)
            EditTextValidator.TEST_DOMAINNAME          -> toAdd = DomainValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_domain_not_valid) else testErrorString)
            EditTextValidator.TEST_IPADDRESS           -> toAdd = IpAddressValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_ip_not_valid) else testErrorString)
            EditTextValidator.TEST_WEBURL              -> toAdd = WebUrlValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_url_not_valid) else testErrorString)
            EditTextValidator.TEST_HTTPS_URL           -> toAdd = HttpsUrlValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_url_not_valid) else testErrorString)
            EditTextValidator.TEST_PERSONNAME          -> toAdd = PersonNameValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_notvalid_personname) else testErrorString)
            EditTextValidator.TEST_PERSONFULLNAME      -> toAdd = PersonFullNameValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_notvalid_personfullname) else testErrorString)
            EditTextValidator.TEST_MIN_LENGTH          -> toAdd = MinDigitLengthValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_not_a_minimum_length) else testErrorString, minLength)

            EditTextValidator.TEST_CUSTOM              -> {
                // must specify the fully qualified class name & an error message
                if (classType == null)
                    throw RuntimeException("Trying to create a custom validator but no classType has been specified.")
                if (TextUtils.isEmpty(testErrorString))
                    throw RuntimeException(String.format("Trying to create a custom validator (%s) but no error string specified.", classType))

                val customValidatorClass: Class<out Validator> = try {
                    this.javaClass.classLoader?.loadClass(classType)?.let {
                        if (!Validator::class.java.isAssignableFrom(it)) {
                            throw RuntimeException(String.format("Custom validator (%s) does not extend %s", classType, Validator::class.java.name))
                        }
                        @Suppress("Unchecked_Cast")
                        it as Class<out Validator>
                    }!!
                } catch (e: ClassNotFoundException) {
                    throw RuntimeException(String.format("Unable to load class for custom validator (%s).", classType))
                }
                toAdd = try {
                    customValidatorClass.getConstructor(String::class.java).newInstance(testErrorString)
                } catch (e: Exception) {
                    throw RuntimeException(String.format("Unable to construct custom validator (%s) with argument: %s", classType,
                        testErrorString))
                }
            }

            EditTextValidator.TEST_DATE                -> toAdd = DateValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_date_not_valid) else testErrorString, customFormat)
            else                                       -> toAdd = DummyValidator()
        }
        val tmpValidator: MultiValidator
        if (!emptyAllowed) { // If the xml tells us that this is a required field, we will add the EmptyValidator.
            tmpValidator = AndValidator()
            tmpValidator.enqueue(EmptyValidator(emptyErrorStringActual))
            tmpValidator.enqueue(toAdd)
        } else {
            tmpValidator = OrValidator(toAdd.errorMessage, NotValidator(null, EmptyValidator(null)), toAdd)
        }
        addValidator(tmpValidator)
    }

    @Suppress("unused")
    fun setClassType(classType: String?, testErrorString: String?, context: Context): DefaultEditTextValidator {
        testType = EditTextValidator.TEST_CUSTOM
        this.classType = classType
        this.testErrorString = testErrorString
        resetValidators(context)
        return this
    }

    @Suppress("unused")
    fun setCustomRegexp(customRegexp: String?, context: Context): DefaultEditTextValidator {
        testType = EditTextValidator.TEST_REGEXP
        this.customRegexp = customRegexp
        resetValidators(context)
        return this
    }

    @Suppress("unused")
    fun setEmptyAllowed(emptyAllowed: Boolean, context: Context): DefaultEditTextValidator {
        this.emptyAllowed = emptyAllowed
        resetValidators(context)
        return this
    }

    private fun setEmptyErrorString(emptyErrorString: String?): DefaultEditTextValidator {
        emptyErrorStringActual = if (!TextUtils.isEmpty(emptyErrorString)) {
            emptyErrorString
        } else {
            defaultEmptyErrorString
        }
        return this
    }

    @Suppress("unused")
    fun setTestErrorString(testErrorString: String?, context: Context): DefaultEditTextValidator {
        this.testErrorString = testErrorString
        resetValidators(context)
        return this
    }

    @Suppress("unused")
    fun setTestType(testType: Int, context: Context): DefaultEditTextValidator {
        this.testType = testType
        resetValidators(context)
        return this
    }

    override fun testValidity(): Boolean {
        return testValidity(true)
    }

    override fun testValidity(showUIError: Boolean): Boolean {
        val isValid = mValidator?.isValid(editTextView) ?: false
        if (!isValid && showUIError) {
            showUIError()
        }
        return isValid
    }

    override fun showUIError() {
        mValidator?.let { mValidator ->
            if (mValidator.hasErrorMessage()) {
                try {
                    val parent = editTextView.parent as TextInputLayout
                    parent.isErrorEnabled = true
                    parent.error = mValidator.errorMessage
                } catch (e: Throwable) {
                    editTextView.error = mValidator.errorMessage
                }
            }
        }
    }

    // might sound like a bug. but there's no way to know if the error is shown (not with public api)
    val isErrorShown: Boolean
        get() = try {
            editTextView.parent as TextInputLayout
            true // might sound like a bug. but there's no way to know if the error is shown (not with public api)
        } catch (e: Throwable) {
            !TextUtils.isEmpty(editTextView.error)
        }

    @Suppress("SpellCheckingInspection")
    data class Parameters(
        val testErrorString: String? = null,
        val emptyAllowed: Boolean = false,
        val testType: Int = EditTextValidator.TEST_NOCHECK,
        val classType: String? = null,
        val customRegexp: String? = null,
        val customFormat: String? = null,
        val emptyErrorStringDef: String? = null,
        var minLength: Int = 0,
        var minNumber: Int = 0,
        var maxNumber: Int = 0,
        var floatminNumber: Float = 0f,
        var floatmaxNumber: Float = 0f
    )
}