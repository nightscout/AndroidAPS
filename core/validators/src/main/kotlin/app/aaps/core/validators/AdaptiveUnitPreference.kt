package app.aaps.core.validators

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.UnitDoubleKey
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveUnitPreference(ctx: Context, attrs: AttributeSet?) : EditTextPreference(ctx, attrs) {

    private val validatorParameters: DefaultEditTextValidator.Parameters
    private var validator: DefaultEditTextValidator? = null
    private val preferenceKey: UnitDoubleKey

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        preferenceKey = preferences.get(key) as UnitDoubleKey
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
        if (preferenceKey.dependency != 0) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            if (!sp.getBoolean(context.getString(preferenceKey.dependency), false))
                isVisible = false
        }
        if (preferenceKey.negativeDependency != 0) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            if (sp.getBoolean(context.getString(preferenceKey.dependency), false))
                isVisible = false
        }
        validatorParameters = obtainValidatorParameters(attrs)
        setOnBindEditTextListener { editText ->
            validator = DefaultEditTextValidator(editText, validatorParameters, context)
            editText.inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL
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

    private fun obtainValidatorParameters(attrs: AttributeSet?): DefaultEditTextValidator.Parameters {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        return DefaultEditTextValidator.Parameters(
            emptyAllowed = typedArray.getBoolean(R.styleable.FormEditText_emptyAllowed, false),
            testType = EditTextValidator.TEST_BG_RANGE,
            testErrorString = typedArray.getString(R.styleable.FormEditText_testErrorString),
            classType = typedArray.getString(R.styleable.FormEditText_classType),
            customRegexp = typedArray.getString(R.styleable.FormEditText_customRegexp),
            emptyErrorStringDef = typedArray.getString(R.styleable.FormEditText_emptyErrorString),
            customFormat = typedArray.getString(R.styleable.FormEditText_customFormat)
        ).also { params ->
            params.minMgdl = preferenceKey.minMgdl
            params.maxMgdl = preferenceKey.maxMgdl
            typedArray.recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        text = profileUtil.fromMgdlToUnits(
            try {
                SafeParse.stringToDouble(getPersistedString(defaultValue as String?))
            } catch (ignored: Exception) {
                getPersistedFloat(preferenceKey.defaultValue.toFloat()).toDouble()
            }, profileUtil.units
        ).toString()
    }

    override fun persistString(value: String?): Boolean =
        try {
            super.persistString(profileUtil.convertToMgdl(SafeParse.stringToDouble(value, preferenceKey.defaultValue), profileUtil.units).toString())
        } catch (ignored: Exception) {
            false
        }
}
