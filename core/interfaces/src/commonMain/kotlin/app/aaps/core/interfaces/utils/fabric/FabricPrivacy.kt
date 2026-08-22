package app.aaps.core.interfaces.utils.fabric

import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Analytics and crash reporting, with the user's opt-out honoured by the implementation.
 *
 * Platform neutral on purpose: the APS plugins report their `determine_basal` failures through
 * [logException], and they are multiplatform. The Android implementation is Firebase; another
 * platform supplies its own without any caller changing.
 *
 * The event parameter map used to be an `android.os.Bundle`. Every caller only ever put `Long`
 * values in it, so it is a plain map here and the implementation converts.
 */
interface FabricPrivacy {

    fun setUserProperty(key: String, value: String)

    /** Logs [name] with no parameters. */
    fun logCustom(event: String)

    /** Logs [name] with numeric parameters, e.g. sizes or counts. */
    fun logCustom(name: String, params: Map<String, Long>)

    fun logMessage(message: String)

    fun logException(throwable: Throwable)

    fun fabricEnabled(): Boolean

    fun logWearException(wearException: EventData.WearException)
}
