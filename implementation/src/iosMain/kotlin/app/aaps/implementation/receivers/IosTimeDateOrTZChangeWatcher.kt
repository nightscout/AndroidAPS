package app.aaps.implementation.receivers

import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTimeZoneChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemClockDidChangeNotification
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.darwin.NSObject
import kotlin.time.Clock

/**
 * Tells the app that the time zone, the daylight saving state or the clock changed.
 *
 * The iOS counterpart of `TimeDateOrTZChangeReceiver`, which does this from Android's
 * `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED`. Nothing did it here, so two things were
 * missing on iOS: [EventTimeZoneChanged] was never sent, and `IobCobCalculatorPlugin` answers that
 * event with a full recalculation - so after a flight the IOB, COB and autosens of the last hours
 * stayed as they had been calculated in the old zone until something else cleared the cache. The
 * pump driver was never told either.
 *
 * iOS posts two notifications where Android sends two intents, but they do not line up with the
 * three cases the pump driver wants to hear about, and one change can post both. So the kind of
 * change is worked out from what actually changed - see [classifyTimeChange] - rather than from which
 * notification arrived.
 *
 * The offset is read with [systemUtcOffsetAt], the same platform function the records store as
 * `utcOffset`, rather than with `NSTimeZone.isDaylightSavingTime`: one source for the offset, and
 * nothing to keep in step.
 *
 * Constructed by the shell rather than injected, next to `IosProtectionLifecycle` and for the same
 * reason: it reaches outside the object graph. Notifications are delivered on the main queue, so the
 * two remembered values are only ever touched by one thread.
 */
class IosTimeDateOrTZChangeWatcher(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val appScope: CoroutineScope,
    private val rxBus: RxBus
) {

    private val observers = mutableListOf<NSObject>()
    private var lastZoneId: String = ""
    private var lastOffset: Long = 0L

    /** Idempotent, so a shell that is restarted does not end up with two sets of observers. */
    fun start() {
        if (observers.isNotEmpty()) return
        lastZoneId = SystemTimeZone.current().id
        lastOffset = systemUtcOffsetAt(Clock.System.now().toEpochMilliseconds())
        aapsLogger.debug(LTag.CORE, "Watching for time and time zone changes, now in $lastZoneId")
        listOf(NSSystemTimeZoneDidChangeNotification, NSSystemClockDidChangeNotification).forEach { name ->
            observers += NSNotificationCenter.defaultCenter.addObserverForName(
                name = name,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
                usingBlock = { onChange() }
            ) as NSObject
        }
    }

    /** Visible for tests, which have no notification centre to post to. */
    internal fun onChange() {
        // First, so that the new zone is what this function and everything the event wakes up read.
        SystemTimeZone.invalidate()
        val zoneId = SystemTimeZone.current().id
        val offset = systemUtcOffsetAt(Clock.System.now().toEpochMilliseconds())
        val type = classifyTimeChange(lastZoneId, lastOffset, zoneId, offset)
        aapsLogger.info(LTag.PUMP, "$type: zone $lastZoneId -> $zoneId, offset $lastOffset -> $offset. Notifying pump driver.")
        lastZoneId = zoneId
        lastOffset = offset

        appScope.launch { activePlugin.activePump.timezoneOrDSTChanged(type) }
        // Only a zone change. A daylight saving change does not need it - the zone's own rules know
        // the offset of every moment, so the same timestamp still converts to the same time of day -
        // and Android does not send it for one either (`TimeDateOrTZChangeReceiverTest`).
        if (type == TimeChangeType.TimezoneChanged) rxBus.send(EventTimeZoneChanged())
    }
}

/**
 * What changed, from the zone name and the offset before and after.
 *
 * - the zone **name** changed -> the phone moved to another zone, whatever the offset did (two zones
 *   can share an offset, and [TimeChangeType.TimezoneChanged] is still the truth)
 * - the name is the same but the offset moved -> daylight saving started or ended. Every zone that
 *   has daylight saving moves the clock **forward** for it, so a larger offset means it started
 * - neither changed -> the clock itself was set. This is why a notification that changed nothing
 *   visible is still reported: a clock set by hand changes neither the zone nor the offset, and
 *   telling the pump driver about it is the whole point of [TimeChangeType.TimeChanged]
 */
internal fun classifyTimeChange(wasZoneId: String, wasOffset: Long, zoneId: String, offset: Long): TimeChangeType =
    when {
        zoneId != wasZoneId -> TimeChangeType.TimezoneChanged
        offset > wasOffset  -> TimeChangeType.DSTStarted
        offset < wasOffset  -> TimeChangeType.DSTEnded
        else                -> TimeChangeType.TimeChanged
    }
