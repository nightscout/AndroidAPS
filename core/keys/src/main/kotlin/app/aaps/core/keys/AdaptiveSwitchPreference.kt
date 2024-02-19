package app.aaps.core.keys

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveSwitchPreference(context: Context, attrs: AttributeSet?, booleanKey: BooleanKey? = null) : SwitchPreference(context, attrs) {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sharedPrefs: SharedPreferences

    constructor(
        ctx: Context,
        booleanKey: BooleanKey,
        @StringRes summary: Int? = null,
        @StringRes title: Int,

        ) : this(ctx, null, booleanKey) {
        key = context.getString(booleanKey.key)
        summary?.let { setSummary(it) }
        this.title = context.getString(title)
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val preferenceKey = booleanKey ?: preferences.get(key) as BooleanKey
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
            if (!sharedPrefs.getBoolean(context.getString(preferenceKey.dependency), false))
                isVisible = false
        }
        if (preferenceKey.negativeDependency != 0) {
            if (sharedPrefs.getBoolean(context.getString(preferenceKey.negativeDependency), false))
                isVisible = false
        }
        setDefaultValue(preferenceKey.defaultValue)
    }

    override fun onAttached() {
        super.onAttached()
        // PreferenceScreen is final so we cannot extend and modify behavior
        val preferenceKey = preferences.get(key) as BooleanKey
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }
}