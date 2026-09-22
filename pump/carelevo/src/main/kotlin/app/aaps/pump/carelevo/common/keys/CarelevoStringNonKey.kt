package app.aaps.pump.carelevo.common.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * CareLevo's stored patch and infusion state, as registered keys rather than raw strings.
 *
 * These were `const val`s in `PrefEnvConfig`, read and written straight through `SP`. That made them
 * invisible twice over: the runtime guard in `PreferencesImpl` could not see them, and - the reason
 * this moved - a settings import cannot tell a key it has never heard of from rubbish left by an old
 * version. Under the removal rule in `_docs/PREFERENCE_MIGRATIONS_PLAN.md` 4.1 decision 4, an
 * unregistered key is trash and gets removed, which here would be the state of a patch that is
 * currently running.
 *
 * **Not exportable, all of them, and that is the decision rather than the default.** This is the state
 * of THIS patch on THIS phone - which infusion is running, what the patch reported, which alarms have
 * been seen. Another phone's copy arriving in an import would be read as the live patch's own. They
 * still survive a restart; that is the preference store, which `exportable` has nothing to do with.
 *
 * The rule applied here (Miloš, 2026-09-22): **these were not exportable before, so they stay not
 * exportable.** None of them was registered, and `PreferencesImpl.isExportableKey` answers false for
 * anything it does not know, so none of them has ever been in an export file. Registering them is
 * plumbing; changing what an export carries would be a behaviour change smuggled in on the back of it.
 *
 * [UserSettingInfo] is the one worth knowing about, so nobody "corrects" it later. Its entity is mixed:
 * `needMaxBolusDoseSyncPatch` and its siblings are per-patch state, but `maxBolusDose`, `maxBasalSpeed`
 * and `lowInsulinNoticeAmount` are limits the user set. It is NOT exportable for the reason above - it
 * never was - and the consequence is that a new phone starts from the patch defaults for those three.
 * If that should change, it is a classification decision for 4.2 step 2, where `kind` can express
 * "in the file, applied only with the pump-configuration checkbox". `exportable` alone cannot say that.
 *
 * **The key strings are unchanged on purpose.** Every one is what is already in a CareLevo user's
 * SharedPreferences today, so changing one would not migrate the value, it would abandon it - and for
 * `PatchInfo` or the infusion records that means a running patch coming back as if it were new. The
 * same rule `CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS` documents for its own
 * misspelt key.
 */
enum class CarelevoStringNonKey(
    override val key: String,
    override val defaultValue: String = "",
    override val exportable: Boolean = false
) : StringNonPreferenceKey {

    PatchInfo("carelevo_patch_info"),
    BasalInfusionInfo("carelevo_basal_infusion_info"),
    TempBasalInfusionInfo("carelevo_temp_basal_infusion_info"),
    ImmeBolusInfusionInfo("carelevo_imme_bolus_infusion_info"),
    ExtendBolusInfusionInfo("carelevo_extend_bolus_infusion_info"),
    UserSettingInfo("carelevo_user_setting_info"),
    AlarmInfoList("carelevo_alarm_info_list"),
    LastSnapshotAlarmCauses("carelevo_last_snapshot_alarm_causes"),
}
