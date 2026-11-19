package app.aaps.pump.carelevo.common

import kotlinx.coroutines.flow.Flow

/**
 * Whether an AAPS screen is in front. The Carelevo alarms need it: a phone notification is shown only while AAPS is
 * in the background, and the stored alarms are read again each time AAPS comes back to the front.
 *
 * Each platform that registers the Carelevo plugin implements it (Android: `AndroidCarelevoAppForeground`).
 */
interface CarelevoAppForeground {

    /** True while AAPS is in front. */
    fun isInForeground(): Boolean

    /** The same as [isInForeground] as a stream: the value at the time of collection first, then each change. */
    fun inForeground(): Flow<Boolean>
}
