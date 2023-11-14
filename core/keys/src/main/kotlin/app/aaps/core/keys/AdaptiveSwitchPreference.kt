package app.aaps.core.keys

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AdaptiveSwitchPreference(context: Context, attrs: AttributeSet?) : AdaptivePreference, SwitchPreference(context, attrs) {

    @Inject lateinit var preferences: Preferences

    private val attributes: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SimpleFullModeSelector)
    override val simpleMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_simpleMode, true)
    override val apsMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_apsMode, true)
    override val nsclientMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_nsclientMode, true)
    override val pumpControlMode: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_pumpControlMode, true)

    // PreferenceScreen is final so we cannot extend and modify behavior
    private val hideParentScreenIfHidden: Boolean = attributes.getBoolean(R.styleable.SimpleFullModeSelector_hideParentScreenIfHidden, false)

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        if (preferences.simpleMode && !simpleMode) isVisible = false
        if (preferences.apsMode && !apsMode) isVisible = false
        if (preferences.nsclientMode && !nsclientMode) isVisible = false
        if (preferences.pumpControlMode && !pumpControlMode) isVisible = false
    }

    override fun onAttached() {
        super.onAttached()
        if (hideParentScreenIfHidden) parent?.isVisible = isVisible
    }
}