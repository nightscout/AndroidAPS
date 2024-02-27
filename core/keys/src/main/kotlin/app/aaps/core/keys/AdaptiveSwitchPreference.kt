package app.aaps.core.keys

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.SwitchPreference
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
    @Inject lateinit var sharedPrefs: SharedPreferences

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, booleanKey = null, title = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        booleanKey?.let { key = context.getString(it.key) }
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
        preferenceKey.dependency?.let {
            if (!sharedPrefs.getBoolean(context.getString(it.key), false))
                isVisible = false
        }
        preferenceKey.negativeDependency?.let {
            if (sharedPrefs.getBoolean(context.getString(it.key), false))
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