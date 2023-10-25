package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class SingleClickButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = com.google.android.material.R.style.Widget_MaterialComponents_Button) :
    MaterialButton(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean = guardClick { super.performClick() }
    override fun callOnClick(): Boolean = guardClick { super.callOnClick() }

    private fun guardClick(block: () -> Boolean): Boolean {
        isEnabled = false
        postDelayed({ isEnabled = true }, BUTTON_REFRACTION_PERIOD)
        return block()
    }

    companion object {

        const val BUTTON_REFRACTION_PERIOD = 3000L
    }
}