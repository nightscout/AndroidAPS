package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertConfiguration
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepRepetitionType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.expiry
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Builds the expiration/expiration-imminent/user-set-expiration [AlertConfiguration]s from
 * current preferences - based on [app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl
 * .createActivationPart2Observables]'s expiration-alert delay math, using O5's own
 * [expiry] extension in place of Dash's pod-state-manager property. Shared between
 * [app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel] (initial
 * programming during activation) and [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin] (re-sync
 * when preferences change afterward) - hoisted here rather than duplicated.
 *
 * Note: unlike Dash, the USER_SET_EXPIRATION reminder is always added to the returned list
 * (with enabled=false when the reminder is disabled) rather than being omitted. The pod's
 * ProgramAlerts command only updates the slots it contains, so omitting it would leave a
 * previously programmed reminder beeping on the pod even after the user disables it.
 */
fun buildO5ExpirationAlerts(podStateManager: O5PodStateManager, preferences: Preferences, aapsLogger: AAPSLogger): List<AlertConfiguration> {
    val userConfiguredExpirationReminderHours =
        preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder).let { enabled ->
            if (enabled) preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours).toLong() else null
        }
    val userConfiguredExpirationAlarmHours =
        preferences.get(OmnipodBooleanPreferenceKey.ExpirationAlarm).let { enabled ->
            if (enabled) preferences.get(OmnipodIntPreferenceKey.ExpirationAlarmHours).toLong() else null
        }

    val podLifeLeft = Duration.between(ZonedDateTime.now(), requireNotNull(podStateManager.expiry) { "Missing pod expiry" })

    val expirationAlarmEnabled = userConfiguredExpirationAlarmHours != null && userConfiguredExpirationAlarmHours > 0
    val expirationAlarmDelay = podLifeLeft.minus(
        Duration.ofHours(userConfiguredExpirationAlarmHours ?: PodConstants.POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT)
    ).plus(Duration.ofHours(8))

    val expirationImminentDelay = podLifeLeft.minus(
        Duration.ofHours(PodConstants.POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING)
    ).plus(Duration.ofHours(8))

    val alerts = mutableListOf(
        AlertConfiguration(
            AlertType.EXPIRATION,
            enabled = expirationAlarmEnabled,
            durationInMinutes = (TimeUnit.HOURS.toMinutes(userConfiguredExpirationAlarmHours ?: PodConstants.POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT) - 60).toShort(),
            autoOff = false,
            AlertTrigger.TimerTrigger(expirationAlarmDelay.toMinutes().toShort()),
            BeepType.FOUR_TIMES_BIP_BEEP,
            BeepRepetitionType.XXX3
        ),
        AlertConfiguration(
            AlertType.EXPIRATION_IMMINENT,
            enabled = expirationAlarmEnabled,
            durationInMinutes = 0,
            autoOff = false,
            AlertTrigger.TimerTrigger(expirationImminentDelay.toMinutes().toShort()),
            BeepType.FOUR_TIMES_BIP_BEEP,
            BeepRepetitionType.XXX4
        )
    )

    val userExpiryReminderEnabled = userConfiguredExpirationReminderHours != null && userConfiguredExpirationReminderHours > 0
    val userExpiryReminderDelay = podLifeLeft.minus(
        Duration.ofHours(userConfiguredExpirationReminderHours ?: (PodConstants.MAX_POD_LIFETIME.toHours() + 1))
    )
    // Always include USER_SET_EXPIRATION in the list.
    val reminderActive = userExpiryReminderEnabled && !userExpiryReminderDelay.isNegative
    if (userExpiryReminderEnabled && userExpiryReminderDelay.isNegative) {
        aapsLogger.warn(LTag.PUMPBTCOMM, "buildO5ExpirationAlerts negative expiryAlertDuration=$userExpiryReminderDelay")
    }
    alerts.add(
        AlertConfiguration(
            AlertType.USER_SET_EXPIRATION,
            enabled = reminderActive,
            durationInMinutes = 0,
            autoOff = false,
            AlertTrigger.TimerTrigger(if (reminderActive) userExpiryReminderDelay.toMinutes().toShort() else 0),
            BeepType.FOUR_TIMES_BIP_BEEP,
            BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
        )
    )
    return alerts
}
