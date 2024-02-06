package app.aaps.core.keys

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveSwitchPreference(context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs) {

    @Inject lateinit var preferences: Preferences

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val preferenceKey = preferences.get(key) as BooleanKey
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
            if (sp.getBoolean(context.getString(preferenceKey.negativeDependency), false))
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