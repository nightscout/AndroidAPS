package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText
import info.nightscout.core.profile.fromMgdlToUnits
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction

class BgRangeValidator(_customErrorMessage: String?, private val min: Int, private val max: Int, private val profileFunction: ProfileFunction) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return try {
            val value = editText.text.toString().toDouble()
            value in Profile.fromMgdlToUnits(min.toDouble(), profileFunction.getUnits())..Profile.fromMgdlToUnits(max.toDouble(), profileFunction.getUnits())
        } catch (e: NumberFormatException) {
            false
        }
    }

}