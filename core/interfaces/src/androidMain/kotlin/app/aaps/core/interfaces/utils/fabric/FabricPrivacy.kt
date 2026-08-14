package app.aaps.core.interfaces.utils.fabric

import android.os.Bundle
import app.aaps.core.interfaces.rx.weardata.EventData

interface FabricPrivacy {

    fun setUserProperty(key: String, value: String)
    fun logCustom(name: String, event: Bundle)
    fun logCustom(event: String)
    fun logMessage(message: String)
    fun logException(throwable: Throwable)
    fun fabricEnabled(): Boolean
    fun logWearException(wearException: EventData.WearException)
}