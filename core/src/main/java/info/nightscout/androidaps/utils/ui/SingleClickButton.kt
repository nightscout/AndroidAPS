package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import info.nightscout.androidaps.core.R
import info.nightscout.shared.logging.StacktraceLoggerWrapper
import org.slf4j.Logger

class SingleClickButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.style.Widget_MaterialComponents_Button) : com.google.android.material.button.MaterialButton(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean = guardClick { super.performClick() }
    override fun callOnClick(): Boolean = guardClick { super.callOnClick() }

    private fun guardClick(block: () -> Boolean): Boolean {
        isEnabled = false
        postDelayed({ isEnabled = true; log.debug("Button enabled") }, BUTTON_REFRACTION_PERIOD)
        return block()
    }

    @Suppress("DEPRECATION")
    private val log: Logger = StacktraceLoggerWrapper.getLogger(SingleClickButton::class.java)

    companion object {
        const val BUTTON_REFRACTION_PERIOD = 3000L
    }
}