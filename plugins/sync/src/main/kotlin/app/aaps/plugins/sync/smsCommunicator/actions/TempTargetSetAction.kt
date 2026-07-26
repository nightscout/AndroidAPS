package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import java.util.concurrent.TimeUnit

/** Activates a preset temp target: TARGET MEAL/ACTIVITY/HYPO. */
class TempTargetSetAction(
    private val reason: TT.Reason,
    private val receivedSms: Sms,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        val units = profileUtil.units
        val ttDuration = preferences.ttDurationMinutes(reason)
        val ttMgdl = preferences.ttTargetMgdl(reason)
        // Await the insert before replying — we're in suspend context, no need to fire-and-forget.
        // If the insert fails we still send the reply, but the user will see no actual TT in the app.
        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = dateUtil.now(),
                duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                reason = reason,
                lowTarget = ttMgdl,
                highTarget = ttMgdl
            ),
            action = Action.TT,
            source = Sources.SMS,
            note = null,
            listValues = listOf(
                ValueWithUnit.Mgdl(ttMgdl),
                ValueWithUnit.Minute(ttDuration)
            )
        )
        val ttDisplay = profileUtil.fromMgdlToUnits(ttMgdl, units)
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(ttDisplay) else decimalFormatter.to0Decimal(ttDisplay)
        val replyText = rh.gs(R.string.smscommunicator_tt_set, ttString, ttDuration)
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
    }
}
