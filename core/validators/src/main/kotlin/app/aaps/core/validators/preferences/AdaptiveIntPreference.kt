package app.aaps.core.validators.preferences

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveIntPreference(
    ctx: Context,
    attrs: AttributeSet? = null,
    intKey: IntPreferenceKey? = null,
    @StringRes dialogMessage: Int? = null,
    @StringRes summary: Int? = null,
    @StringRes title: Int?,
    validatorParams: DefaultEditTextValidator.Parameters? = null
) : EditTextPreference(ctx, attrs) {

    private val validatorParameters: DefaultEditTextValidator.Parameters
    private var validator: DefaultEditTextValidator? = null
    private val preferenceKey: IntPreferenceKey

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, intKey = null, title = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        intKey?.let { key = it.key }
        dialogMessage?.let { setDialogMessage(it) }
        summary?.let { setSummary(it) }
        title?.let { dialogTitle = context.getString(it) }
        title?.let { this.title = context.getString(it) }

        preferenceKey = intKey ?: preferences.get(key) as IntPreferenceKey
        if (preferences.simpleMode && preferenceKey.defaultedBySM) isVisible = false
        if (preferences.apsMode && !preferenceKey.showInApsMode) {
            isVisible = false; isEnabled = false
        }
        if (preferences.nsclientMode && !preferenceKey.showInNsClientMode) {
            isVisible = false; isEnabled = false
        }
        if (preferences.pumpControlMode && !preferenceKey.showInPumpControlMode) {
            isVisible = false; isEnabled = false
        }
        if (!config.isEngineeringMode() && preferenceKey.engineeringModeOnly) {
            isVisible = false; isEnabled = false
        }
        preferenceKey.dependency?.let {
            if (!preferences.get(it))
                isVisible = false
        }
        preferenceKey.negativeDependency?.let {
            if (preferences.get(it))
                isVisible = false
        }
        validatorParameters = validatorParams ?: obtainValidatorParameters(attrs)
        setOnBindEditTextListener { editText ->
            validator = DefaultEditTextValidator(editText, validatorParameters, context)
            if (preferenceKey.min < 0)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            else
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.setSelectAllOnFocus(true)
            editText.setSingleLine()
            editText.setSelection(editText.length())
        }
        setOnPreferenceChangeListener { _, _ -> validator?.testValidity(false) != false }
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

    private fun obtainValidatorParameters(attrs: AttributeSet?): DefaultEditTextValidator.Parameters {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        return DefaultEditTextValidator.Parameters(
            emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false),
            testType = EditTextValidator.TEST_NUMERIC_RANGE,
            testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString),
            classType = typedArray.getString(R.styleable.FormEditText_classType),
            customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp),
            emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString),
            customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)
        ).also { params ->
            params.minNumber = preferenceKey.min
            params.maxNumber = preferenceKey.max
            typedArray.recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        text = try {
            getPersistedInt(preferenceKey.defaultValue).toString()
        } catch (_: Exception) {
            getPersistedString(defaultValue as String?)
        }
    }

    override fun persistString(value: String?): Boolean =
        try {
            super.persistInt(SafeParse.stringToInt(value))
        } catch (_: Exception) {
            super.persistString(SafeParse.stringToInt(value).toString())
        }
}
