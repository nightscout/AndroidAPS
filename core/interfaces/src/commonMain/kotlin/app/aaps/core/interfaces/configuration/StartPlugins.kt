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
 * the app from ever calculating either - see [initializeAndAnnounceReady].
 *
 * One value for every path on purpose. `MainApp`, `ConfigBuilderImpl.applyConfiguration` and this
 * all bound the same wait, and each used to carry its own copy of the number with a comment saying
 * it had to match the others. A number that must match in three places is a number that will not.
 */
val PLUGIN_SETTLE_WAIT: Duration = 30.seconds

/**
 * Starts the plugins, then says the app is ready - once they really are.
 *
 * [EventAppInitialized] is what starts the first IOB/COB calculation: `IobCobCalculatorPlugin`
 * collects it and runs the main calculation on it. Without it nothing is calculated until some
 * other change comes along, which on a client means the next glucose reading - up to five minutes
 * of an empty graph after every start (nightscout/AndroidAPS#5485).
 *
 * Only `MainApp` sent the event, so only Android had this. The listener is shared code, so the iOS
 * and the desktop shell both had the same empty graph; this is here rather than in each shell so
 * the next platform gets it by using it.
 *
 * ### Why it waits
 *
 * [ConfigBuilder.initialize] only *schedules* the plugins' `onStart`, and the calculator subscribes
 * in its own `onStart`. Sending the event straight away would be sending it to nobody: the
 * collector is started `UNDISPATCHED` precisely because this event fires once and must not be
 * missed, which only helps if the subscription already exists. So the jobs are waited for first.
 *
 * Waiting also closes the other half of the same gap. Both shells threw the returned jobs away,
 * which [ConfigBuilder.initialize] warns about in as many words.
 *
 * The wait is bounded and the event is sent even when it runs out, with a warning. One pump driver
 * that never finishes starting should cost the log line, not every calculation from then on.
 *
 * Android keeps its own send: it has more start-up work to do afterwards and announces at the end
 * of all of it, not here.
 *
 * @return the job doing the waiting, so a caller - or a test - can await the announcement.
 */
fun ConfigBuilder.initializeAndAnnounceReady(
    scope: CoroutineScope,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    wait: Duration = PLUGIN_SETTLE_WAIT
): Job = announceReadyWhenStarted(initialize(), scope, rxBus, aapsLogger, wait)

/**
 * [initializeAndAnnounceReady] for a caller that already holds the start jobs.
 *
 * The iOS shell starts the plugins through its own `PluginRegistry`, so it has the jobs rather than
 * the builder. Same waiting and the same announcement, so the two shells cannot drift apart.
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
