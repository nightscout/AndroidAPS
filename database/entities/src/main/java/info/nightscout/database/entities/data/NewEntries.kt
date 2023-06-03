package info.nightscout.database.entities.data

import info.nightscout.database.entities.APSResult
import info.nightscout.database.entities.APSResultLink
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.HeartRate
import info.nightscout.database.entities.MultiwaveBolusLink
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.PreferenceChange
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.TotalDailyDose
import info.nightscout.database.entities.VersionChange

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
    val versionChanges: List<VersionChange>,
    val heartRates: List<HeartRate>,
)
