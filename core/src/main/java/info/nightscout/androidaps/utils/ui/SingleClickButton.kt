package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class SingleClickButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.style.Widget_MaterialComponents_Button) :
    com.google.android.material.button.MaterialButton(context, attrs, defStyleAttr) {

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun performClick(): Boolean = guardClick { super.performClick() }
    override fun callOnClick(): Boolean = guardClick { super.callOnClick() }

    private fun guardClick(block: () -> Boolean): Boolean {
        isEnabled = false
        postDelayed({ isEnabled = true; aapsLogger.debug(LTag.UI, "Button enabled") }, BUTTON_REFRACTION_PERIOD)
        return block()
    }

    companion object {

        const val BUTTON_REFRACTION_PERIOD = 3000L
    }
}