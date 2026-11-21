package app.aaps.core.interfaces.configuration

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long any start path waits for the plugins to settle before carrying on regardless.
 *
 * A driver whose `onStart` never finishes must not hold up start up for ever, and it must not keep
 * the app from ever calculating either - see [announceReadyWhenStarted].
 *
 * One value for every path on purpose. `AppStartSequence`, `ConfigBuilderImpl.applyConfiguration` and
 * this all bound the same wait, and each used to carry its own copy of the number with a comment saying
 * it had to match the others. A number that must match in three places is a number that will not.
 */
val PLUGIN_SETTLE_WAIT: Duration = 30.seconds

/**
 * Says the app is ready once the plugins really have started.
 *
 * [EventAppInitialized] is what starts the first IOB/COB calculation: `IobCobCalculatorPlugin`
 * collects it and runs the main calculation on it. Without it nothing is calculated until some
 * other change comes along, which on a client means the next glucose reading - up to five minutes
 * of an empty graph after every start (nightscout/AndroidAPS#5485).
 *
 * ### Why it waits
 *
 * [ConfigBuilder.initialize] only *schedules* the plugins' `onStart`, and the calculator subscribes
 * in its own `onStart`. Sending the event straight away would be sending it to nobody: the
 * collector is started `UNDISPATCHED` precisely because this event fires once and must not be
 * missed, which only helps if the subscription already exists. So the jobs are waited for first.
 *
 * The wait is bounded and the event is sent even when it runs out, with a warning. One pump driver
 * that never finishes starting should cost the log line, not every calculation from then on.
 *
 * Only the iOS shell still uses this. Android and the desktop run `AppStartSequence`, which waits the
 * same way and announces at the end of the whole start. Once iOS runs it too, this can go.
 *
 * @return the job doing the waiting, so a caller - or a test - can await the announcement.
 */
fun announceReadyWhenStarted(
    started: List<Job>,
    scope: CoroutineScope,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    wait: Duration = PLUGIN_SETTLE_WAIT
): Job = scope.launch {
    if (withTimeoutOrNull(wait) { started.joinAll() } == null)
        aapsLogger.warn(LTag.CORE, "Plugins did not finish starting within $wait, saying the app is ready anyway")
    aapsLogger.debug(LTag.CORE, "Plugins started, the app is initialized")
    rxBus.send(EventAppInitialized())
}
