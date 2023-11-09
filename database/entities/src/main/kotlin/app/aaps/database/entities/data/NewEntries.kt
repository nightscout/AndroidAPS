package app.aaps.database.entities.data

import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.MultiwaveBolusLink
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.PreferenceChange
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.VersionChange

data class NewEntries(
    val apsResults: List<app.aaps.database.entities.APSResult>,
    val apsResultLinks: List<app.aaps.database.entities.APSResultLink>,
    val bolusCalculatorResults: List<BolusCalculatorResult>,
    val boluses: List<app.aaps.database.entities.Bolus>,
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
