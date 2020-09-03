package info.nightscout.androidaps.utils.textValidator

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import info.nightscout.androidaps.core.R

class ValidatingEditTextPreference(ctx: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int)
    : EditTextPreference(ctx, attrs, defStyleAttr, defStyleRes) {

    private lateinit var validatorParameters: DefaultEditTextValidator.Parameters
    private var validator: DefaultEditTextValidator? = null

    init {
        obtainValidatorParameters(attrs)

        setOnBindEditTextListener { editText ->
            validator = DefaultEditTextValidator(editText, validatorParameters, context)
        }
        setOnPreferenceChangeListener { preference, newValue ->
            validator?.testValidity(false) ?: true
        }
    }

    constructor(ctx: Context, attrs: AttributeSet, defStyle: Int)
        : this(ctx, attrs, defStyle, 0)

    constructor(ctx: Context, attrs: AttributeSet)
        : this(ctx, attrs, R.attr.editTextPreferenceStyle)

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.isDividerAllowedAbove = false
        holder?.isDividerAllowedBelow = false
    }

    private fun obtainValidatorParameters(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        validatorParameters = DefaultEditTextValidator.Parameters()
        validatorParameters.emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false)
        validatorParameters.testType = typedArray.getInt(R.styleable.FormEditText_testType, EditTextValidator.TEST_NOCHECK)
        validatorParameters.testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString)
        validatorParameters.classType = typedArray.getString(R.styleable.FormEditText_classType)
        validatorParameters.customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp)
        validatorParameters.emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString)
        validatorParameters.customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)
        if (validatorParameters.testType == EditTextValidator.TEST_MIN_LENGTH)
            validatorParameters.minLength = typedArray.getInt(R.styleable.FormEditText_minLength, 0)
        if (validatorParameters.testType == EditTextValidator.TEST_NUMERIC_RANGE) {
            validatorParameters.minNumber = typedArray.getInt(R.styleable.FormEditText_minNumber, Int.MIN_VALUE)
            validatorParameters.maxNumber = typedArray.getInt(R.styleable.FormEditText_maxNumber, Int.MAX_VALUE)
        }
        if (validatorParameters.testType == EditTextValidator.TEST_FLOAT_NUMERIC_RANGE) {
            validatorParameters.floatminNumber = typedArray.getFloat(R.styleable.FormEditText_floatminNumber, Float.MIN_VALUE)
            validatorParameters.floatmaxNumber = typedArray.getFloat(R.styleable.FormEditText_floatmaxNumber, Float.MAX_VALUE)
        }
        typedArray.recycle()
    }
}
