package info.nightscout.androidaps.utils.textValidator

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import info.nightscout.androidaps.core.R

class ValidatingEditTextPreference(ctx: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int)
    : EditTextPreference(ctx, attrs, defStyleAttr, defStyleRes) {

    private val validatorParameters: DefaultEditTextValidator.Parameters = obtainValidatorParameters(attrs)
    private var validator: DefaultEditTextValidator? = null

    init {
        setOnBindEditTextListener { editText ->
            validator = DefaultEditTextValidator(editText, validatorParameters, context)
        }
        setOnPreferenceChangeListener { _, _ ->
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

    private fun obtainValidatorParameters(attrs: AttributeSet): DefaultEditTextValidator.Parameters {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        return DefaultEditTextValidator.Parameters(
            emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false),
            testType = typedArray.getInt(R.styleable.FormEditText_testType, EditTextValidator.TEST_NOCHECK),
            testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString),
            classType = typedArray.getString(R.styleable.FormEditText_classType),
            customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp),
            emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString),
            customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)).also { params ->
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
            typedArray.recycle()
        }
    }
}
