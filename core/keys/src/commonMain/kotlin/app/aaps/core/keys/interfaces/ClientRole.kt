package app.aaps.core.keys.interfaces

/**
 * What a paired client may do on the master. Chosen per client on the master's Authorized clients
 * screen; the master enforces it on every command, and the client hides what its role cannot use.
 *
 * The roles form a ladder in this order: each one includes everything the one before it may do. The
 * master itself is always [Admin].
 *
 * - [ReadOnly]: stays paired, sends nothing but its pairing hello and liveness pings.
 * - [MealAssistant]: bolus, bolus wizard, QuickWizard, carbs and eCarbs, stop a bolus, dismiss the
 *   bolus-failed alarm.
 * - [Caregiver]: uses everything (temp targets, profile and insulin switches, loop mode, temp basal,
 *   extended bolus, scenes, careportal and device events, record edits and deletes, calibration), but
 *   does not change settings or presets.
 * - [Admin]: also defines presets and changes synced settings.
 */
enum class ClientRole {

    ReadOnly,
    MealAssistant,
    Caregiver,
    Admin;

    /** Whether this role may do something that needs [required]. */
    fun allows(required: ClientRole): Boolean = this >= required

    companion object {

        /**
         * The role stored or sent under [name].
         *
         * No name at all is [Admin]: a client stored or published before roles existed had full control,
         * so it keeps it. A name this build does not know (a role added later, read after going back to an
         * older build) is [ReadOnly], the safe side.
         */
        fun of(name: String?): ClientRole =
            if (name.isNullOrBlank()) Admin
            else entries.firstOrNull { it.name == name } ?: ReadOnly
    }
}
