package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder, reporting [BgQualityCheck.State.UNKNOWN] - which is a real state the interface
 * already defines and every reader already handles, not an invented one.
 *
 * `BgQualityCheckPlugin` decides whether the glucose data being read is trustworthy: whether it is
 * genuine five minute data, recalculated, doubled up, or flat for 45 minutes because a sensor has
 * stopped moving. All of that is arithmetic over the last hour of readings and nothing about it is
 * Android - it is in androidMain only because it has not been moved. It should be ported rather
 * than written again here.
 *
 * Saying UNKNOWN is the honest placeholder: it means "no judgement has been made", which is exactly
 * true. Claiming FIVE_MIN_DATA would tell the user their data had been checked and passed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosBgQualityCheck @Inject constructor(
    private val aapsLogger: AAPSLogger
) : BgQualityCheck {

    private val _stateFlow = MutableStateFlow(BgQualityCheck.State.UNKNOWN)
    override val stateFlow: StateFlow<BgQualityCheck.State> = _stateFlow.asStateFlow()

    override var state: BgQualityCheck.State
        get() = _stateFlow.value
        set(value) {
            _stateFlow.value = value
        }

    override var message: String = ""

    override fun stateDescription(): String {
        aapsLogger.notOnIosYet("BgQualityCheck.stateDescription")
        return ""
    }
}
