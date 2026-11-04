package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.ReadParameterBlockMessage
import app.aaps.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.connection.ActivateServiceMessage
import app.aaps.pump.insight.app_layer.connection.BindMessage
import app.aaps.pump.insight.app_layer.connection.ConnectMessage
import app.aaps.pump.insight.app_layer.connection.DisconnectMessage
import app.aaps.pump.insight.app_layer.connection.ServiceChallengeMessage
import app.aaps.pump.insight.app_layer.history.ReadHistoryEventsMessage
import app.aaps.pump.insight.app_layer.history.StartReadingHistoryMessage
import app.aaps.pump.insight.app_layer.history.StopReadingHistoryMessage
import app.aaps.pump.insight.app_layer.remote_control.CancelBolusMessage
import app.aaps.pump.insight.app_layer.remote_control.CancelTBRMessage
import app.aaps.pump.insight.app_layer.remote_control.ChangeTBRMessage
import app.aaps.pump.insight.app_layer.remote_control.ConfirmAlertMessage
import app.aaps.pump.insight.app_layer.remote_control.DeliverBolusMessage
import app.aaps.pump.insight.app_layer.remote_control.GetAvailableBolusTypesMessage
import app.aaps.pump.insight.app_layer.remote_control.SetDateTimeMessage
import app.aaps.pump.insight.app_layer.remote_control.SetOperatingModeMessage
import app.aaps.pump.insight.app_layer.remote_control.SetTBRMessage
import app.aaps.pump.insight.app_layer.remote_control.SnoozeAlertMessage
import app.aaps.pump.insight.app_layer.status.GetActiveAlertMessage
import app.aaps.pump.insight.app_layer.status.GetActiveBasalRateMessage
import app.aaps.pump.insight.app_layer.status.GetActiveBolusesMessage
import app.aaps.pump.insight.app_layer.status.GetActiveTBRMessage
import app.aaps.pump.insight.app_layer.status.GetBatteryStatusMessage
import app.aaps.pump.insight.app_layer.status.GetCartridgeStatusMessage
import app.aaps.pump.insight.app_layer.status.GetDateTimeMessage
import app.aaps.pump.insight.app_layer.status.GetFirmwareVersionsMessage
import app.aaps.pump.insight.app_layer.status.GetOperatingModeMessage
import app.aaps.pump.insight.app_layer.status.GetPumpStatusRegisterMessage
import app.aaps.pump.insight.app_layer.status.GetTotalDailyDoseMessage
import app.aaps.pump.insight.app_layer.status.ResetPumpStatusRegisterMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [AppCommands.Companion]: the two lookup tables that turn a pump command id into a message
 * object and back again.
 *
 * Both tables are written out by hand, entry by entry, and nothing makes them agree. If one of them
 * gains a wrong line, a command goes out under another command's id, and the pump answers as if a
 * different thing had been asked of it. That is the failure these tests are here to catch, so they
 * check the pair together rather than either one on its own.
 */
class AppCommandsTest {

    /** One row per command the pump understands: the id on the wire, and the message carrying it. */
    private val commands: List<Pair<Int, Class<out AppLayerMessage>>> = listOf(
        AppCommands.CONNECTMESSAGE to ConnectMessage::class.java,
        AppCommands.BINDMESSAGE to BindMessage::class.java,
        AppCommands.DISCONNECTMESSAGE to DisconnectMessage::class.java,
        AppCommands.ACTIVATESERVICEMESSAGE to ActivateServiceMessage::class.java,
        AppCommands.SERVICECHALLENGEMESSAGE to ServiceChallengeMessage::class.java,
        AppCommands.GETACTIVEALERTMESSAGE to GetActiveAlertMessage::class.java,
        AppCommands.GETACTIVEBOLUSESMESSAGE to GetActiveBolusesMessage::class.java,
        AppCommands.GETACTIVETBRMESSAGE to GetActiveTBRMessage::class.java,
        AppCommands.GETAVAILABLEBOLUSTYPESMESSAGE to GetAvailableBolusTypesMessage::class.java,
        AppCommands.GETBATTERYSTATUSMESSAGE to GetBatteryStatusMessage::class.java,
        AppCommands.GETCARTRIDGESTATUSMESSAGE to GetCartridgeStatusMessage::class.java,
        AppCommands.GETDATETIMEMESSAGE to GetDateTimeMessage::class.java,
        AppCommands.GETFIRMWAREVERSIONSMESSAGE to GetFirmwareVersionsMessage::class.java,
        AppCommands.GETOPERATINGMODEMESSAGE to GetOperatingModeMessage::class.java,
        AppCommands.GETPUMPSTATUSREGISTERMESSAGE to GetPumpStatusRegisterMessage::class.java,
        AppCommands.RESETPUMPSTATUSREGISTERMESSAGE to ResetPumpStatusRegisterMessage::class.java,
        AppCommands.GETACTIVEBASALRATEMESSAGE to GetActiveBasalRateMessage::class.java,
        AppCommands.GETTOTALDAILYDOSEMESSAGE to GetTotalDailyDoseMessage::class.java,
        AppCommands.CANCELTBRMESSAGE to CancelTBRMessage::class.java,
        AppCommands.CANCELBOLUSMESSAGE to CancelBolusMessage::class.java,
        AppCommands.SETOPERATINGMODEMESSAGE to SetOperatingModeMessage::class.java,
        AppCommands.READPARAMETERBLOCKMESSAGE to ReadParameterBlockMessage::class.java,
        AppCommands.WRITECONFIGURATIONBLOCKMESSAGE to WriteConfigurationBlockMessage::class.java,
        AppCommands.CLOSECONFIGURATIONWRITESESSIONMESSAGE to CloseConfigurationWriteSessionMessage::class.java,
        AppCommands.OPENCONFIGURATIONWRITESESSIONMESSAGE to OpenConfigurationWriteSessionMessage::class.java,
        AppCommands.DELIVERBOLUSMESSAGE to DeliverBolusMessage::class.java,
        AppCommands.SETTBRMESSAGE to SetTBRMessage::class.java,
        AppCommands.CHANGETBRMESSAGE to ChangeTBRMessage::class.java,
        AppCommands.READHISTORYEVENTSMESSAGE to ReadHistoryEventsMessage::class.java,
        AppCommands.STARTREADINGHISTORYMESSAGE to StartReadingHistoryMessage::class.java,
        AppCommands.STOPREADINGHISTORYMESSAGE to StopReadingHistoryMessage::class.java,
        AppCommands.CONFIRMALERTMESSAGE to ConfirmAlertMessage::class.java,
        AppCommands.SNOOZEALERTMESSAGE to SnoozeAlertMessage::class.java,
        AppCommands.SETDATETIMEMESSAGE to SetDateTimeMessage::class.java
    )

    @Test
    fun fromId_buildsTheMessageThatBelongsToEachId() {
        val wrong = commands.filter { (id, type) ->
            val message = AppCommands.fromId(id)
            message == null || !type.isInstance(message)
        }
        assertThat(wrong.map { it.first }).isEmpty()
    }

    @Test
    fun fromType_givesBackTheIdTheMessageCameFrom() {
        val wrong = commands.filter { (id, _) -> AppCommands.fromType(AppCommands.fromId(id)) != id }
        assertThat(wrong.map { it.first }).isEmpty()
    }

    /** Two commands sharing an id would make one of them unreachable, whichever table is read. */
    @Test
    fun theIdsAreAllDifferent() {
        val ids = commands.map { it.first }
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    fun fromId_returnsNullForAnIdThePumpNeverSends() {
        assertThat(AppCommands.fromId(0)).isNull()
        assertThat(AppCommands.fromId(-1)).isNull()
        assertThat(AppCommands.fromId(Int.MAX_VALUE)).isNull()
    }

    /** `fromType` answers 0 for null, which the caller reads as "no id", not as a real command. */
    @Test
    fun fromType_returnsZeroForNoMessage() {
        assertThat(AppCommands.fromType(null)).isEqualTo(0)
        assertThat(commands.map { it.first }).doesNotContain(0)
    }
}
