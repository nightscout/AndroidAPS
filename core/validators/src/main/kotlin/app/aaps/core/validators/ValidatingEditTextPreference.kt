package app.aaps.core.validators

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ValidatingEditTextPreference(ctx: Context, attrs: AttributeSet?) : EditTextPreference(ctx, attrs) {

    private val validatorParameters: DefaultEditTextValidator.Parameters = obtainValidatorParameters(attrs)
    private var validator: DefaultEditTextValidator? = null

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    private val attributes: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SimpleFullModeSelector)
    private val simpleMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_simpleMode, true)
    private val apsMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_apsMode, true)
    private val nsclientMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_nsclientMode, true)
    private val pumpControlMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_pumpControlMode, true)

    // PreferenceScreen is final so we cannot extend and modify behavior
    private val hideParentScreenIfHidden: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_hideParentScreenIfHidden, false)

    init {
        setOnBindEditTextListener { editText -> validator = DefaultEditTextValidator(editText, validatorParameters, context) }
        setOnPreferenceChangeListener { _, _ -> validator?.testValidity(false) != false }
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        if (preferences.simpleMode && !simpleMode) isVisible = false
        if (preferences.apsMode && !apsMode) isVisible = false
        if (preferences.nsclientMode && !nsclientMode) isVisible = false
        if (preferences.pumpControlMode && !pumpControlMode) isVisible = false
    }

    override fun onAttached() {
        super.onAttached()
        if (hideParentScreenIfHidden) parent?.isVisible = isVisible
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
    }

    fun setMinNumber(min: Int) {
        this.validatorParameters.minNumber = min
    }

    fun setMaxNumber(max: Int) {
        this.validatorParameters.maxNumber = max
    }

    private fun obtainValidatorParameters(attrs: AttributeSet?): DefaultEditTextValidator.Parameters {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        return DefaultEditTextValidator.Parameters(
            emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false),
            testType = typedArray.getInt(R.styleable.FormEditText_testType, EditTextValidator.TEST_NOCHECK),
            testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString),
            classType = typedArray.getString(R.styleable.FormEditText_classType),
            customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp),
            emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString),
            customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)
        ).also { params ->
            if (params.testType == EditTextValidator.TEST_MIN_LENGTH)
                params.minLength = typedArray.getInt(R.styleable.FormEditText_minLength, 0)
            if (params.testType == EditTextValidator.TEST_NUMERIC_RANGE) {
                params.minNumber = typedArray.getInt(R.styleable.FormEditText_minNumber, Int.MIN_VALUE)
                params.maxNumber = typedArray.getInt(R.styleable.FormEditText_maxNumber, Int.MAX_VALUE)
            }
            if (params.testType == EditTextValidator.TEST_FLOAT_NUMERIC_RANGE) {
                params.floatminNumber = typedArray.getFloat(R.styleable.FormEditText_floatminNumber, Float.MIN_VALUE)
                params.floatmaxNumber = typedArray.getFloat(R.styleable.FormEditText_floatmaxNumber, Float.MAX_VALUE)
            }
            if (params.testType == EditTextValidator.TEST_BG_RANGE) {
                params.minMgdl = typedArray.getInt(R.styleable.FormEditText_minMgdl, Int.MIN_VALUE)
                params.maxMgdl = typedArray.getInt(R.styleable.FormEditText_maxMgdl, Int.MAX_VALUE)
            }
            typedArray.recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        text =
            if (validatorParameters.testType == EditTextValidator.TEST_BG_RANGE)
                profileUtil.valueInCurrentUnitsDetect(SafeParse.stringToDouble(getPersistedString(defaultValue as String?))).toString()
            else
                getPersistedString(defaultValue as String?)
    }

    override fun persistString(value: String?): Boolean =
        when (validatorParameters.testType) {
            EditTextValidator.TEST_BG_RANGE            -> super.persistString(profileUtil.convertToMgdl(SafeParse.stringToDouble(value, 0.0), profileUtil.units).toString())
            EditTextValidator.TEST_FLOAT_NUMERIC_RANGE -> super.persistString(SafeParse.stringToDouble(value, 0.0).toString())
            else                                       -> super.persistString(value)
        }
}
