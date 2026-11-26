package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds of the core's own BG alarms (source [SOURCE]), evaluated on every stored reading on the master and on
 * every client (`_docs/ALARMS.md`, step 2). The ids are stored with the user's choice: never change one.
 */
enum class BgAlert(
    private val idName: String,
    override val title: TextRef,
    override val isProtected: Boolean,
    override val group: AlertGroup? = null,
    override val defaultChoice: AlertChoice = AlertChoice.RING
) : AlertKind {

    /** Always on, always rings, and also in Do Not Disturb. The user sets only its threshold. */
    URGENT_LOW("urgent_low", InterfacesStrings.alert_kind_bg_urgent_low, isProtected = true),

    /** The user's own low rules ([BgAlarmRule], [BgDirection.LOW]). */
    LOW("low", InterfacesStrings.alert_kind_bg_low, isProtected = false, AlertGroup.BG_LOWS),

    /** The user's own high rules. */
    HIGH("high", InterfacesStrings.alert_kind_bg_high, isProtected = false, AlertGroup.BG_HIGHS),

    /**
     * No reading for longer than the stale data threshold: a booked alert, booked again at every reading (step 2b).
     * Off until the user turns it on, as its old switch was.
     */
    STALE("stale", InterfacesStrings.alert_kind_bg_stale, isProtected = false, defaultChoice = AlertChoice.OFF),

    /** The user's persistent high rules ([BgRuleType.PERSISTENT_HIGH], step 2c). */
    PERSISTENT_HIGH("persistent_high", InterfacesStrings.alert_kind_bg_persistent_high, isProtected = false, AlertGroup.BG_HIGHS),

    /** The user's falling fast rules ([BgRuleType.RATE], [BgDirection.LOW]). */
    FALLING_FAST("falling_fast", InterfacesStrings.alert_kind_bg_falling_fast, isProtected = false, AlertGroup.BG_LOWS),

    /** The user's rising fast rules ([BgRuleType.RATE], [BgDirection.HIGH]). */
    RISING_FAST("rising_fast", InterfacesStrings.alert_kind_bg_rising_fast, isProtected = false, AlertGroup.BG_HIGHS),

    /** The user's predicted low rules ([BgRuleType.PREDICTED_LOW]): from the loop's prediction. */
    PREDICTED_LOW("predicted_low", InterfacesStrings.alert_kind_bg_predicted_low, isProtected = false, AlertGroup.BG_LOWS);

    // A getter: the companion object is not there yet while the entries are built
    override val id: String get() = "$SOURCE.$idName"
    override val source: String get() = SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.APP
    override val section: AlertSection get() = AlertSection.BG

    // Every phone raises its own from the same readings: an announcement would make a client hear it twice
    override val announced: Boolean get() = false

    /**
     * How long a silence lasts where the user does not pick one: a swipe, the watch, Mute (decided 2026-11-26, it was a
     * setting of each rule). Urgent low comes back soon, a high can wait an hour. The alarm screen has its own buttons.
     */
    val silenceMinutes: Int
        get() = when (this) {
            URGENT_LOW                              -> 15
            HIGH, PERSISTENT_HIGH, RISING_FAST      -> 60
            LOW, FALLING_FAST, PREDICTED_LOW, STALE -> 30
        }

    companion object {

        /** The source every BG alarm is raised under. */
        const val SOURCE = "bg"

        /** What "pause the lows" pauses (`_docs/ALARMS.md`, step 2d). Never urgent low. */
        val LOWS: List<BgAlert> get() = listOf(LOW, FALLING_FAST, PREDICTED_LOW)

        /** What "pause the highs" pauses. */
        val HIGHS: List<BgAlert> get() = listOf(HIGH, PERSISTENT_HIGH, RISING_FAST)

        /** What "pause all" pauses: every BG alarm but urgent low. */
        val PAUSABLE: List<BgAlert> get() = entries.filterNot { it.isProtected }
    }
}

/**
 * The BG alarms: watches the stored readings and raises or ends the level alerts. Started once by the app start; a
 * platform without an alert core (the desktop) has none.
 */
interface BgAlarms {

    /** Starts watching the readings and the rules. Called once, after the app start. */
    fun start()
}
