package app.aaps.core.validators.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveClickPreference(
    ctx: Context,
    attrs: AttributeSet? = null,
    stringKey: StringPreferenceKey? = null,
    @StringRes summary: Int? = null,
    @StringRes title: Int?,
    onPreferenceClickListener: OnPreferenceClickListener? = null,
    val calculatedVisibility: (() -> Boolean)? = null
) : Preference(ctx, attrs) {

    private val preferenceKey: StringPreferenceKey

    @Inject lateinit var preferences: Preferences

    // Inflater constructor
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, stringKey = null, title = null)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        stringKey?.let { key = it.key }
        summary?.let { setSummary(it) }
        title?.let { this.title = context.getString(it) }
        onPreferenceClickListener?.let { setOnPreferenceClickListener(it) }

        preferenceKey = stringKey ?: preferences.get(key) as StringPreferenceKey
        if (preferences.simpleMode && preferenceKey.defaultedBySM) {
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
        preferenceKey.dependency?.let {
            if (!preferences.get(it))
                isVisible = false
        }
        preferenceKey.negativeDependency?.let {
            if (preferences.get(it))
                isVisible = false
        }
        calculatedVisibility?.let { isVisible = it.invoke() }
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
}