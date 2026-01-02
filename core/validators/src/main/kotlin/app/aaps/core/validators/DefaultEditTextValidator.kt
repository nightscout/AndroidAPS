package app.aaps.core.validators

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.validators.validators.AlphaNumericValidator
import app.aaps.core.validators.validators.AlphaValidator
import app.aaps.core.validators.validators.AndValidator
import app.aaps.core.validators.validators.BgRangeValidator
import app.aaps.core.validators.validators.CreditCardValidator
import app.aaps.core.validators.validators.DateValidator
import app.aaps.core.validators.validators.DomainValidator
import app.aaps.core.validators.validators.DummyValidator
import app.aaps.core.validators.validators.EmailValidator
import app.aaps.core.validators.validators.EmptyValidator
import app.aaps.core.validators.validators.FloatNumericRangeValidator
import app.aaps.core.validators.validators.HttpsUrlValidator
import app.aaps.core.validators.validators.IpAddressValidator
import app.aaps.core.validators.validators.MinDigitLengthValidator
import app.aaps.core.validators.validators.MultiPhoneValidator
import app.aaps.core.validators.validators.MultiValidator
import app.aaps.core.validators.validators.NotValidator
import app.aaps.core.validators.validators.NumericRangeValidator
import app.aaps.core.validators.validators.NumericValidator
import app.aaps.core.validators.validators.OrValidator
import app.aaps.core.validators.validators.PersonFullNameValidator
import app.aaps.core.validators.validators.PersonNameValidator
import app.aaps.core.validators.validators.PhoneValidator
import app.aaps.core.validators.validators.PinStrengthValidator
import app.aaps.core.validators.validators.RegexpValidator
import app.aaps.core.validators.validators.Validator
import app.aaps.core.validators.validators.WebUrlValidator
import com.google.android.material.textfield.TextInputLayout
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class DefaultEditTextValidator : EditTextValidator {

    private var mValidator: MultiValidator? = null
    private var testErrorString: String? = null
    private var emptyAllowed = false
    private lateinit var editTextView: EditText
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
    private var minMgdl = 0
    private var maxMgdl = 0
    private var floatminNumber = 0f
    private var floatmaxNumber = 0f

    @Inject lateinit var profileUtil: ProfileUtil

    @Suppress("unused")
    constructor(editTextView: EditText, context: Context) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        testType = EditTextValidator.TEST_NOCHECK
        setEditText(editTextView)
        resetValidators(context)
    }

    constructor(editTextView: EditText, parameters: Parameters, context: Context) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
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
        minMgdl = parameters.minMgdl
        maxMgdl = parameters.maxMgdl
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
        editTextView = editText
        editText.addTextChangedListener(textWatcher)
        editText.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}

            override fun onViewDetachedFromWindow(view: View) {
                editText.removeOnAttachStateChangeListener(this)
                editText.removeTextChangedListener(textWatcher)
            }
        })
    }

    val textWatcher =
        object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                testValidity()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { /* not needed */
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!TextUtils.isEmpty(s) && isErrorShown) {
                    try {
                        val textInputLayout = editTextView.parent as TextInputLayout
                        textInputLayout.isErrorEnabled = false
                    } catch (_: Throwable) {
                        editTextView.error = null
                    }
                }
            }
        }

    override fun isEmptyAllowed(): Boolean = emptyAllowed

    override fun resetValidators(context: Context) {
        // its possible the context may have changed so re-get the defaultEmptyErrorString
        defaultEmptyErrorString = context.getString(R.string.error_field_must_not_be_empty)
        setEmptyErrorString(emptyErrorStringDef)
        mValidator = AndValidator()
        val toAdd: Validator =
            when (testType) {
                EditTextValidator.TEST_NOCHECK             -> DummyValidator()
                EditTextValidator.TEST_ALPHA               -> AlphaValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_standard_letters_are_allowed) else testErrorString)
                EditTextValidator.TEST_ALPHANUMERIC        -> AlphaNumericValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_this_field_cannot_contain_special_character) else testErrorString)
                EditTextValidator.TEST_NUMERIC             -> NumericValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_only_numeric_digits_allowed) else testErrorString)
                EditTextValidator.TEST_NUMERIC_RANGE       -> NumericRangeValidator(
                    if (TextUtils.isEmpty(testErrorString)) context.getString(
                        R.string.error_only_numeric_digits_range_allowed,
                        minNumber.toString(),
                        maxNumber.toString()
                    ) else testErrorString, minNumber, maxNumber
                )

                EditTextValidator.TEST_FLOAT_NUMERIC_RANGE -> FloatNumericRangeValidator(
                    if (TextUtils.isEmpty(testErrorString)) context.getString(
                        R.string.error_only_numeric_digits_range_allowed,
                        floatminNumber.toString(),
                        floatmaxNumber.toString()
                    ) else testErrorString, floatminNumber, floatmaxNumber
                )

                EditTextValidator.TEST_REGEXP              -> RegexpValidator(testErrorString, customRegexp ?: "")
                EditTextValidator.TEST_CREDITCARD          -> CreditCardValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_creditcard_number_not_valid) else testErrorString)
                EditTextValidator.TEST_EMAIL               -> EmailValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_email_address_not_valid) else testErrorString)
                EditTextValidator.TEST_PHONE               -> PhoneValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_phone_not_valid) else testErrorString)
                EditTextValidator.TEST_MULTI_PHONE         -> MultiPhoneValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_phone_not_valid) else testErrorString)
                EditTextValidator.TEST_PIN_STRENGTH        -> PinStrengthValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_pin_not_valid) else testErrorString)
                EditTextValidator.TEST_DOMAINNAME          -> DomainValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_domain_not_valid) else testErrorString)
                EditTextValidator.TEST_IPADDRESS           -> IpAddressValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_ip_not_valid) else testErrorString)
                EditTextValidator.TEST_WEBURL              -> WebUrlValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_url_not_valid) else testErrorString)
                EditTextValidator.TEST_HTTPS_URL           -> HttpsUrlValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_url_not_valid) else testErrorString)
                EditTextValidator.TEST_PERSONNAME          -> PersonNameValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_notvalid_personname) else testErrorString)
                EditTextValidator.TEST_PERSONFULLNAME      -> PersonFullNameValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_notvalid_personfullname) else testErrorString)
                EditTextValidator.TEST_MIN_LENGTH          -> MinDigitLengthValidator(
                    if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_not_a_minimum_length) else testErrorString,
                    minLength
                )

                EditTextValidator.TEST_BG_RANGE            -> BgRangeValidator(
                    if (TextUtils.isEmpty(testErrorString)) context.getString(
                        R.string.error_only_numeric_digits_range_allowed,
                        profileUtil.fromMgdlToUnits(minMgdl.toDouble()).toString(), profileUtil.fromMgdlToUnits(maxMgdl.toDouble()).toString()
                    ) else testErrorString, minMgdl, maxMgdl, profileUtil
                )

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
                    } catch (_: ClassNotFoundException) {
                        throw RuntimeException(String.format("Unable to load class for custom validator (%s).", classType))
                    }
                    try {
                        customValidatorClass.getConstructor(String::class.java).newInstance(testErrorString)
                    } catch (_: Exception) {
                        throw RuntimeException(String.format("Unable to construct custom validator (%s) with argument: %s", classType, testErrorString))
                    }
                }

                EditTextValidator.TEST_DATE                -> DateValidator(if (TextUtils.isEmpty(testErrorString)) context.getString(R.string.error_date_not_valid) else testErrorString, customFormat)
                else                                       -> DummyValidator()
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
        val isValid = mValidator?.isValid(editTextView) == true
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
                } catch (_: Throwable) {
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
        } catch (_: Throwable) {
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
        var minMgdl: Int = 0,
        var maxMgdl: Int = 0,
        var floatminNumber: Float = 0f,
        var floatmaxNumber: Float = 0f
    )
}