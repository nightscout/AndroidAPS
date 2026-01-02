package app.aaps.core.validators.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.Preference
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveIntentPreference(
    ctx: Context,
    attrs: AttributeSet? = null,
    intentKey: IntentPreferenceKey?,
    intent: Intent? = null,
    @StringRes summary: Int? = null,
    @StringRes title: Int? = null
) : Preference(ctx, attrs) {

    @Inject lateinit var preferences: Preferences

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, intentKey = null, intent = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        intentKey?.let { key = it.key }
        summary?.let { setSummary(it) }
        title?.let { this.title = context.getString(it) }
        this.intent = intent

        val preferenceKey = intentKey ?: preferences.get(key) as IntentPreferenceKey
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
            if (!preferences.get(it))
                isVisible = false
        }
        preferenceKey.negativeDependency?.let {
            if (preferences.get(it))
                isVisible = false
        }
    }

    override fun onAttached() {
        super.onAttached()
        // PreferenceScreen is final so we cannot extend and modify behavior
        val preferenceKey = preferences.get(key) as IntentPreferenceKey
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }
}