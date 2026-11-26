package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * What one row of the alert settings sets on this phone: a group of kinds ([AlertGroup]) or one kind ([AlertKind])
 * (`_docs/ALARMS.md`, section 6). The user's choice and sound are stored under [id].
 */
interface AlertSetting {

    /** Unique in the whole app and stable: the user's choice and sound are stored under it. Never change it. */
    val id: String

    /** Its name in the alert settings. */
    val title: TextRef

    /** It always rings: the user cannot set it to Silent or Off. */
    val isProtected: Boolean

    /**
     * What this phone does with it while the user chose nothing: Ring, but Off for an alert that was off until the
     * user turned it on (missed BG readings, decided 2026-11-26). A choice equal to it is not stored, unless it is an
     * exception, which is set against its group.
     */
    val defaultChoice: AlertChoice get() = AlertChoice.RING
}

/** The parts of the alert settings, in the order they are shown. */
enum class AlertSection {

    /** The pump: the driver's alerts and the app's alerts about the pump. */
    PUMP,

    /** The CGM: the driver's device and error alerts. Its glucose alarms are [BG]'s. */
    CGM,

    /** AAPS's own BG alarms. */
    BG,

    /** The app's other alerts, one row each: the user made them, or they ask for something only the user knows. */
    APP
}

/**
 * Kinds that share one choice and one sound on this phone (decided 2026-11-26): the user sets how loud the pump or the
 * CGM is, not each of its alarm names. A kind of a group can still be set on its own, as an exception: its own choice
 * wins over the group's.
 *
 * A device's kind is in the group of what its events do to dosing ([of], decision 14), so a new driver gets its
 * settings without a list of its own, and the settings stay when the user changes the pump.
 */
enum class AlertGroup(
    private val idName: String,
    val section: AlertSection,
    override val title: TextRef,
    /** One line under its name: what is in it. */
    val summary: TextRef,
    override val isProtected: Boolean,
    /** False for an info group: an info card has no sound, so its choices are only shown or Off. */
    val makesSound: Boolean = true
) : AlertSetting {

    /** Dosing affected now, or soon unless the user acts. */
    PUMP_CRITICAL("pump_critical", AlertSection.PUMP, InterfacesStrings.alert_group_critical, InterfacesStrings.alert_group_critical_summary, isProtected = true),

    /** The user has to do something, but dosing is not affected: a reminder, a failed command to check. */
    PUMP_ACTION("pump_action", AlertSection.PUMP, InterfacesStrings.alert_group_action, InterfacesStrings.alert_group_action_summary, isProtected = false),

    /** For information only. */
    PUMP_INFO("pump_info", AlertSection.PUMP, InterfacesStrings.alert_group_info, InterfacesStrings.alert_group_info_summary, isProtected = false, makesSound = false),

    CGM_CRITICAL("cgm_critical", AlertSection.CGM, InterfacesStrings.alert_group_critical, InterfacesStrings.alert_group_critical_summary, isProtected = true),
    CGM_ACTION("cgm_action", AlertSection.CGM, InterfacesStrings.alert_group_action, InterfacesStrings.alert_group_action_summary, isProtected = false),
    CGM_INFO("cgm_info", AlertSection.CGM, InterfacesStrings.alert_group_info, InterfacesStrings.alert_group_info_summary, isProtected = false, makesSound = false),

    /** What "pause the lows" pauses ([BgAlert.LOWS]). */
    BG_LOWS("bg_lows", AlertSection.BG, InterfacesStrings.alert_group_lows, InterfacesStrings.alert_group_lows_summary, isProtected = false),

    /** What "pause the highs" pauses ([BgAlert.HIGHS]). */
    BG_HIGHS("bg_highs", AlertSection.BG, InterfacesStrings.alert_group_highs, InterfacesStrings.alert_group_highs_summary, isProtected = false);

    // Its own prefix: a kind's id starts with its source
    override val id: String = "group.$idName"

    /** The choices the user has for it and for its exceptions, in the order they are shown. */
    val choices: List<AlertChoice>
        get() = if (makesSound) AlertChoice.entries else listOf(AlertChoice.RING, AlertChoice.OFF)

    companion object {

        /**
         * The group of a pump's or CGM's kind from what its events do to dosing: one that affects dosing is critical, one
         * that asks for an action needs action, the rest is info.
         */
        fun of(origin: AlertOrigin, effects: Collection<DosingEffect>): AlertGroup {
            val pump = when (origin) {
                AlertOrigin.PUMP -> true
                AlertOrigin.CGM  -> false
                else             -> throw IllegalArgumentException("Only a pump or a CGM has device groups: $origin")
            }
            return when {
                effects.any { it.affectsDosing }  -> if (pump) PUMP_CRITICAL else CGM_CRITICAL
                DosingEffect.ACTION in effects -> if (pump) PUMP_ACTION else CGM_ACTION
                else                              -> if (pump) PUMP_INFO else CGM_INFO
            }
        }
    }
}
