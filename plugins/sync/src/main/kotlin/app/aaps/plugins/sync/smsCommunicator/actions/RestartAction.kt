package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Restarts AAPS: RESTART. */
class RestartAction(
    private val receivedSms: Sms,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val configBuilder: ConfigBuilder,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        uel.log(
            Action.EXIT_AAPS, Sources.SMS,
            rh.gs(R.string.smscommunicator_restarting),
            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_restarting))
        )
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_restarting)))
        configBuilder.exitApp("SMS", Sources.SMS, true)
    }
}
