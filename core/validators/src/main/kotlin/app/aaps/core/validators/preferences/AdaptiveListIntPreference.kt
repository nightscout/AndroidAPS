package app.aaps.core.validators.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import javax.inject.Inject

open class AdaptiveListIntPreference(
    ctx: Context,
    attrs: AttributeSet? = null,
    intKey: IntPreferenceKey?,
    @StringRes title: Int?,
    @StringRes dialogMessage: Int? = null,
    @StringRes dialogTitle: Int? = null,
    @StringRes summary: Int? = null,
    entries: Array<CharSequence>? = null,
    entryValues: Array<CharSequence>? = null
) : ListPreference(ctx, attrs) {

    @Inject lateinit var preferences: Preferences

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, intKey = null, title = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        intKey?.let { key = it.key }
        title?.let { this.title = context.getString(it) }
        dialogMessage?.let { this.dialogMessage = context.getString(it) }
        dialogTitle?.let { this.dialogTitle = context.getString(it) }
        summary?.let { this.summary = context.getString(it) }
        entries?.let { setEntries(it) }
        entryValues?.let { setEntryValues(it) }

        val preferenceKey = intKey ?: preferences.get(key) as IntPreferenceKey
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
        setDefaultValue(preferenceKey.defaultValue.toString())
    }

    override fun onAttached() {
        super.onAttached()
        // PreferenceScreen is final so we cannot extend and modify behavior
        val preferenceKey = preferences.get(key) as IntPreferenceKey
        if (preferenceKey.hideParentScreenIfHidden) {
            parent?.isVisible = isVisible
            parent?.isEnabled = isEnabled
        }
    }
}