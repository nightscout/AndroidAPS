package app.aaps.core.validators

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.Preferences
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveDoublePreference(ctx: Context, attrs: AttributeSet?) : EditTextPreference(ctx, attrs) {

    private val validatorParameters: DefaultEditTextValidator.Parameters
    private var validator: DefaultEditTextValidator? = null
    private val preferenceKey: DoubleKey

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        preferenceKey = preferences.get(key) as DoubleKey
        if (preferences.simpleMode && (preferenceKey.defaultedBySM || preferenceKey.calculatedBySM)) {
            isVisible = false; isEnabled = false
        }
        if (preferences.apsMode && !preferenceKey.showInApsMode) {
            isVisible = false; isEnabled = false
        }
        if (preferences.nsclientMode && !preferenceKey.showInNsClientMode) {
            isVisible = false; isEnabled = false
        }
        if (preferences.pumpControlMode && !preferenceKey.showInPumpControlMode) {
            isVisible = false; isEnabled = false
        }
        validatorParameters = obtainValidatorParameters(attrs)
        setOnBindEditTextListener { editText ->
            validator = DefaultEditTextValidator(editText, validatorParameters, context)
            editText.inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL
            editText.setSelectAllOnFocus(true)
            editText.setSingleLine()
        }
        setOnPreferenceChangeListener { _, _ -> validator?.testValidity(false) ?: true }
        setDefaultValue(preferenceKey.defaultValue)
    }

    override fun onAttached() {
        super.onAttached()
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
    }

    fun setMinNumber(min: Double) {
        this.validatorParameters.floatminNumber = min.toFloat()
    }

    fun setMaxNumber(max: Int) {
        this.validatorParameters.floatmaxNumber = max.toFloat()
    }

    private fun obtainValidatorParameters(attrs: AttributeSet?): DefaultEditTextValidator.Parameters {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        return DefaultEditTextValidator.Parameters(
            emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false),
            testType = EditTextValidator.TEST_FLOAT_NUMERIC_RANGE,
            testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString),
            classType = typedArray.getString(R.styleable.FormEditText_classType),
            customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp),
            emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString),
            customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)
        ).also { params ->
            params.floatminNumber = preferenceKey.min.toFloat()
            params.floatmaxNumber = preferenceKey.max.toFloat()
            typedArray.recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        text = try {
            getPersistedString(defaultValue as String?)
        } catch (ignored: Exception) {
            getPersistedFloat(preferenceKey.defaultValue.toFloat()).toString()
        }
    }

    override fun persistString(value: String?): Boolean =
        try {
            super.persistString(SafeParse.stringToDouble(value, 0.0).toString())
        } catch (ignored: Exception) {
            false
        }
}
