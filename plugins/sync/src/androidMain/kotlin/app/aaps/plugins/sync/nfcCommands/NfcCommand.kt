package app.aaps.plugins.sync.nfcCommands

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One command as stored on a tag.
 *
 * The format has been through three shapes. The first was a string split on spaces, which could not
 * carry a profile or scene name containing one. The second was hand written `org.json`, which fixed
 * that but read every value through `optDouble` / `optInt` / `optBoolean` against a loose string key,
 * so a missing or mistyped key returned a default instead of failing - a silently wrong dose rather
 * than an error. This is the third: the same JSON structure, described by types.
 *
 * A command that cannot be decoded returns null from [decode] rather than throwing, because a tag is
 * outside the app's control and a bad one must not take the screen down with it.
 */
@Serializable
data class NfcCommand(
    val code: NfcCommandCode,
    val params: NfcParams = NfcParams()
) {

    fun encode(): String = json.encodeToString(this)

    companion object {

        /**
         * Lenient on unknown keys so a tag written by a newer build still reads here, strict on
         * everything else: a value of the wrong type fails the whole command instead of quietly
         * becoming a default.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        /** The command in [text], or null when it is not one. */
        fun decode(text: String): NfcCommand? =
            runCatching { json.decodeFromString<NfcCommand>(text) }.getOrNull()
    }
}

/**
 * Everything a command can carry.
 *
 * Every field is optional because each command uses only a few of them; which ones is decided by the
 * action's `argType` list. Nullable rather than defaulted where a missing value is a real problem, so
 * an action can tell "not set" from "set to zero".
 *
 * The tag's name is deliberately **not** here. It belongs to the tag, not to a command, and it used to
 * be copied into every command of a chain and read back at 25 call sites only to fill the user entry
 * note. It is passed to `NfcAction.execute` instead.
 */
@Serializable
data class NfcParams(
    /** Insulin units, for BOLUS and EXTENDED_SET. */
    val insulin: Double? = null,
    /** Carbs in grams, for CARBS and BOLUS_WIZARD. */
    val carbs: Int? = null,
    /** Glucose target, in the display unit, for the manual temp target. */
    val glucose: Double? = null,
    /** Percentage, for profile switch, the wizard, and percent basal. */
    val percent: Int? = null,
    /** Minutes, for temp targets, basal, suspend and disconnect. */
    val duration: Int? = null,
    /** Units per hour, for absolute basal. */
    val rate: Double? = null,
    val profileName: String? = null,
    val sceneId: String? = null,
    /** Bolus only: also start an Eating Soon temporary target. */
    val isMeal: Boolean = false,

    // Bolus wizard calculation toggles
    val useBg: Boolean = true,
    val useTt: Boolean = true,
    val useTrend: Boolean = true,
    val useIob: Boolean = true,
    val useCob: Boolean = true
)
