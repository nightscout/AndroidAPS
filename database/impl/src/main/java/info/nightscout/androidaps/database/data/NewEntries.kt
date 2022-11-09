package info.nightscout.androidaps.database.data

import info.nightscout.androidaps.database.entities.*

data class NewEntries(
    val apsResults: List<APSResult>,
    val apsResultLinks: List<APSResultLink>,
    val bolusCalculatorResults: List<BolusCalculatorResult>,
    val boluses: List<Bolus>,
    val carbs: List<Carbs>,
    val effectiveProfileSwitches: List<EffectiveProfileSwitch>,
    val extendedBoluses: List<ExtendedBolus>,
    val glucoseValues: List<GlucoseValue>,
    val multiwaveBolusLinks: List<MultiwaveBolusLink>,
    val offlineEvents: List<OfflineEvent>,
    val preferencesChanges: List<PreferenceChange>,
    val profileSwitches: List<ProfileSwitch>,
    val temporaryBasals: List<TemporaryBasal>,
    val temporaryTarget: List<TemporaryTarget>,
    val therapyEvents: List<TherapyEvent>,
    val totalDailyDoses: List<TotalDailyDose>,
    val versionChanges: List<VersionChange>
)