package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventBTChange
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Always empty: iOS does not report other apps' Bluetooth connections.
 *
 * On Android this buffer is filled by a broadcast receiver watching `ACTION_ACL_CONNECTED` and
 * `ACTION_ACL_DISCONNECTED`, which fire for **any** device the phone connects to - a car, a headset,
 * a watch. That is what the Bluetooth trigger is for. iOS has no equivalent: CoreBluetooth reports
 * only peripherals this app itself connects to, so the events the trigger waits for never occur.
 *
 * The consequence is worth being plain about: a Bluetooth trigger can be configured on iOS and will
 * never fire. The log line is there so that shows up as a stated fact rather than as silence.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosBtConnectionSource @Inject constructor(
    private val aapsLogger: AAPSLogger
) : BtConnectionSource {

    override fun recentBtConnects(): List<EventBTChange> {
        aapsLogger.debug(LTag.AUTOMATION, "Bluetooth connection events are not visible on iOS, a BT trigger cannot fire")
        return emptyList()
    }
}
