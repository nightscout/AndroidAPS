package app.aaps.pump.carelevo.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.R

/**
 * Only the parameters a key actually varies are declared. Everything else - `calculatedDefaultValue`,
 * `engineeringModeOnly`, `defaultedBySM`, the three `showIn*` flags, both dependencies,
 * `hideParentScreenIfHidden`, `exportable` and `entries` - already has the same default on
 * [IntPreferenceKey] / [app.aaps.core.keys.interfaces.PreferenceKey], so restating it here was only a
 * second place to keep in step.
 *
 * [min] and [max] are the exception: they are abstract on [IntPreferenceKey], so they have to be stated
 * even though both keys are LIST preferences that never use them.
 */
enum class CarelevoIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    private val titleResId: Int = 0,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD
) : IntPreferenceKey {

    CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS(
        "CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS",
        116,
        R.string.carelevo_patch_expiration_reminders_title_value,
        preferenceType = PreferenceType.LIST
    ),
    /**
     * How much insulin is left when the patch should remind - an amount in units, not hours and not an
     * expiry. It is sent as `NoticeThresholdCommand(TYPE_LOW_INSULIN, value)`, whose documented range is
     * 20..50 U, and the default of 30 is a reservoir level.
     *
     * The stored key still reads `..._EXPIRATION_REMINDER_HOURS` on purpose: it is what is already in
     * every user's SharedPreferences, so changing the string would silently reset the threshold they
     * configured back to the default.
     */
    CARELEVO_LOW_INSULIN_REMINDER_UNITS(
        "CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS",
        30,
        R.string.carelevo_low_reservoir_reminders_title_value,
        preferenceType = PreferenceType.LIST
    )
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
