package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Sends a calibration to xDrip: CAL <value>. */
class CalibrationAction(
    val value: Double,
    private val receivedSms: Sms,
    private val xDripBroadcast: XDripBroadcast,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        val result = xDripBroadcast.sendCalibration(value)
        val replyText =
            if (result) rh.gs(R.string.smscommunicator_calibration_sent) else rh.gs(R.string.smscommunicator_calibration_failed)
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
        if (result)
            uel.log(
                Action.CALIBRATION, Sources.SMS, rh.gs(R.string.smscommunicator_calibration_sent),
                ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_calibration_sent))
            )
        else
            uel.log(
                Action.CALIBRATION, Sources.SMS, rh.gs(R.string.smscommunicator_calibration_failed),
                ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_calibration_failed))
            )
    }
}
