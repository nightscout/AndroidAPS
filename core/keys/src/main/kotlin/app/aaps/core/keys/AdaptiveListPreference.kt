package app.aaps.core.keys

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import dagger.android.HasAndroidInjector
import javax.inject.Inject

open class AdaptiveListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    @Inject lateinit var preferences: Preferences

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val preferenceKey = preferences.get(key) as StringKey
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
        setDefaultValue(preferenceKey.defaultValue)
    }

    override fun onAttached() {
        super.onAttached()
        // PreferenceScreen is final so we cannot extend and modify behavior
        val preferenceKey = preferences.get(key) as StringKey
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }
}