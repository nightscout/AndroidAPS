package app.aaps.plugins.sync.nsclientV3.ws

import kotlinx.coroutines.flow.Flow

/**
 * One step of the Nightscout load round.
 *
 * Named rather than typed, so shared code can say what should run without naming a worker class.
 * Each platform maps a step to whatever it uses to run it - on Android that is a `Worker`.
 */
enum class NsLoadStep {
    STATUS,
    LAST_MODIFICATION,
    BG,
    TREATMENTS,
    FOODS,
    PROFILE_STORE,
    SETTINGS,
    DEVICE_STATUS,
    DATA_SYNC
}

/**
 * Runs the Nightscout load and upload rounds, under one name that only one round may hold.
 *
 * Same split as [app.aaps.plugins.sync.nsclientV3.ws.NsConnection]: the plugin decides *what* should
 * run and *whether* it may, this decides *how* it runs. Android uses WorkManager, because a round
 * started from the UI should finish even if the screen goes away.
 *
 * The whole round shares a single name. That is what makes [isRunning] meaningful, and it is why a
 * new round replaces an in-flight one rather than running beside it: two rounds writing the same
 * high-water marks would interleave.
 */
interface NsLoadExecutor {

    /** True while any part of the round is enqueued, blocked or running. */
    val isRunning: Boolean

    /**
     * Emits when the round goes from active to idle.
     *
     * The plugin queues a follow-up round rather than dropping one that arrives while another is in
     * flight, and this is what tells it the queue has drained.
     */
    val idle: Flow<Unit>

    /** Runs [steps] in order under the shared name, replacing any round already going. */
    fun runChain(steps: List<NsLoadStep>)

    /** Runs one step under the shared name, replacing any round already going. */
    fun runReplacing(step: NsLoadStep)

    /**
     * Runs one step on its own, outside the shared name.
     *
     * Deliberately not part of a round: it must not replace one, and a round in flight must not
     * stop it.
     */
    fun runDetached(step: NsLoadStep)

    /** Cancels the round, if one is going. */
    fun cancel()
}
