package app.aaps.core.validators.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.SwitchPreference
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveSwitchPreference(
    ctx: Context,
    attrs: AttributeSet? = null,
    booleanKey: BooleanPreferenceKey?,
    @StringRes summary: Int? = null,
    @StringRes title: Int?
) : SwitchPreference(ctx, attrs) {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, booleanKey = null, title = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        booleanKey?.let { key = it.key }
        summary?.let { setSummary(it) }
        title?.let { this.title = context.getString(it) }

        val preferenceKey = booleanKey ?: preferences.get(key) as BooleanPreferenceKey
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
        setDefaultValue(preferenceKey.defaultValue)
    }

    override fun onAttached() {
        super.onAttached()
        // PreferenceScreen is final so we cannot extend and modify behavior
        val preferenceKey = preferences.get(key) as BooleanPreferenceKey
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }
}