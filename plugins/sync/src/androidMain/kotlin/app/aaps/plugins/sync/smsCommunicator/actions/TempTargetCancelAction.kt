package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Cancels an active temp target: TARGET STOP/CANCEL. */
class TempTargetCancelAction(
    private val receivedSms: Sms,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val uel: UserEntryLogger,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    /**
     * The cancel is awaited, not launched.
     *
     * [run] is already `suspend`, so the `appScope.launch` this used to do bought nothing and cost
     * two things: the work outlived the plugin on the application scope, and - worse - the "temp
     * target canceled" SMS went out immediately afterwards, before the cancel had actually happened
     * and whether or not it succeeded. The user was told the target was off while it might still be
     * running. Awaiting puts the reply after the fact it reports.
     */
    override suspend fun run() {
        persistenceLayer.cancelCurrentTemporaryTargetIfAny(
            timestamp = dateUtil.now(),
            action = Action.CANCEL_TT,
            source = Sources.SMS,
            note = rh.gs(SyncStrings.smscommunicator_tt_canceled),
            listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(SyncStrings.smscommunicator_tt_canceled)))
        )
        val replyText = rh.gs(SyncStrings.smscommunicator_tt_canceled)
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
        uel.log(
            Action.CANCEL_TT, Sources.SMS, rh.gs(SyncStrings.smscommunicator_tt_canceled),
            ValueWithUnit.SimpleString(rh.gsNotLocalised(SyncStrings.smscommunicator_tt_canceled))
        )
    }
}
