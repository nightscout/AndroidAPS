package app.aaps.plugins.sync.smsCommunicator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * SMS remote control, which iOS does not have.
 *
 * Both halves are impossible, not merely unimplemented. An iOS app cannot send an SMS without a
 * person tapping send in a system sheet, and cannot read an incoming one at all - there is no
 * permission, entitlement or private arrangement that changes either. So this exists to say no
 * rather than to be finished later.
 *
 * The SMS plugin is always disabled on iOS, so nothing should ever reach this. It exists because
 * other code injects [SmsCommunicator] whether or not the plugin is on - the binding has to be
 * satisfiable for the graph to build at all. Think of it as the answer to "what if someone asks
 * anyway", not as a feature waiting to be written.
 *
 * [isEnabled] returning false is the important part. Callers check it before doing anything, so the
 * feature is switched off at the point of use rather than appearing available and then failing per
 * message. That is the difference between a capability being visibly absent and one being present
 * and dead.
 *
 * The send methods still return false and log rather than throwing: a caller that ignores
 * [isEnabled] should be recorded, not crashed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSmsCommunicator @Inject constructor(
    private val aapsLogger: AAPSLogger
) : SmsCommunicator {

    /** Always empty. Nothing can arrive, so nothing is ever recorded. */
    override var messages: ArrayList<Sms> = ArrayList()

    /** False, and callers gate on it. */
    override fun isEnabled(): Boolean = false

    override fun sendNotificationToAllNumbers(text: String): Boolean {
        aapsLogger.debug(LTag.SMS, "iOS cannot send SMS, dropping notification to all numbers")
        return false
    }

    override fun sendSMS(sms: Sms): Boolean {
        aapsLogger.debug(LTag.SMS, "iOS cannot send SMS, dropping message")
        return false
    }
}
