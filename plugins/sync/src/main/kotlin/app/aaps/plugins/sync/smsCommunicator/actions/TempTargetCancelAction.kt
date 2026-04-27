package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Cancels an active temp target: TARGET STOP/CANCEL. */
class TempTargetCancelAction(
    private val receivedSms: Sms,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val appScope: CoroutineScope,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        appScope.launch {
            persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                timestamp = dateUtil.now(),
                action = Action.CANCEL_TT,
                source = Sources.SMS,
                note = rh.gs(R.string.smscommunicator_tt_canceled),
                listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled)))
            )
        }
        val replyText = rh.gs(R.string.smscommunicator_tt_canceled)
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
        uel.log(
            Action.CANCEL_TT, Sources.SMS, rh.gs(R.string.smscommunicator_tt_canceled),
            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled))
        )
    }
}
