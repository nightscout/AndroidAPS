package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The [ConnectivityGate] for Nightscout, with the Nightscout connection settings. */
@SingleIn(AppScope::class)
@Inject
class ReceiverDelegate(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    receiverStatusStore: ReceiverStatusStore
) : ConnectivityGate(aapsLogger, rh, receiverStatusStore, NightscoutConnectivitySettings(preferences), LTag.NSCLIENT)
