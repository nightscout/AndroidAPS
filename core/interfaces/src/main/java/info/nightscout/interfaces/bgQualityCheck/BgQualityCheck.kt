package info.nightscout.interfaces.bgQualityCheck

import androidx.annotation.DrawableRes

interface BgQualityCheck {
    var message: String
    @DrawableRes fun icon(): Int
    fun stateDescription(): String
}