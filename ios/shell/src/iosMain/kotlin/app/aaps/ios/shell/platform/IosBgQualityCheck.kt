package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Always [BgQualityCheck.State.UNKNOWN], because a follower client has no judgement to make.
 *
 * This is not a placeholder waiting for `BgQualityCheckPlugin` to be ported. It is the answer that
 * belongs to a client build, and it is worth saying why, because "always UNKNOWN" reads like an
 * unfinished class:
 *
 * - **The APS plugins are here, but they never dose.** `OpenAPSSMBPlugin` and `OpenAPSAutoISFPlugin`
 *   are registered on every build, client included - `ApsPluginRegistrations` contributes them to the
 *   **unqualified** plugin map on purpose, so a follower still shows them in its plugin list. That is
 *   why this binding has to exist at all: the plugins are constructed and take a `BgQualityCheck`.
 *   What does not happen is the decision - `config.APS` is false, so nothing asks them to dose, and
 *   the state is never read for anything.
 * - **The sensor is not this device's.** The remaining caller is the overview badge that warns about
 *   flat or doubled readings, and it is shown beside the **BG source setup**. A client's glucose
 *   arrives from Nightscout, already judged by the master that owns the sensor. Re-deciding here
 *   would mean second-guessing that from data this device did not collect, and disagreeing with the
 *   master about glucose quality is worse than saying nothing.
 *
 * UNKNOWN is a state the interface defines and every reader already handles - it means "no judgement
 * has been made", which is exactly true. The badge simply does not appear.
 *
 * If an iOS build ever runs the loop, this stops being right and `BgQualityCheckPlugin` has to be
 * ported. That is the same condition that makes several other client-shaped answers here wrong, and
 * it is the point at which this file should be deleted rather than edited.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosBgQualityCheck @Inject constructor() : BgQualityCheck {

    override var state: BgQualityCheck.State
        get() = BgQualityCheck.State.UNKNOWN
        set(_) {
            // Ignored on purpose. Only BgQualityCheckPlugin writes this, and it does not run here.
        }

    override var message: String = ""

    /** Never changes, so a collector sees UNKNOWN once and nothing after. */
    override val stateFlow: StateFlow<BgQualityCheck.State> = MutableStateFlow(BgQualityCheck.State.UNKNOWN)

    /** Empty rather than a sentence: the badge this describes is never shown on a client. */
    override fun stateDescription(): String = ""
}
