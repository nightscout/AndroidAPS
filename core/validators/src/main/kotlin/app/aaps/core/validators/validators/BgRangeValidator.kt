package app.aaps.core.validators.validators

import android.widget.EditText
import app.aaps.core.interfaces.profile.ProfileUtil

class BgRangeValidator(customErrorMessage: String?, private val min: Int, private val max: Int, private val profileUtil: ProfileUtil) : Validator(customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return try {
            val value = editText.text.toString().toDouble()
            value in profileUtil.fromMgdlToUnits(min.toDouble())..profileUtil.fromMgdlToUnits(max.toDouble())
        } catch (e: NumberFormatException) {
            false
        }
    }

}