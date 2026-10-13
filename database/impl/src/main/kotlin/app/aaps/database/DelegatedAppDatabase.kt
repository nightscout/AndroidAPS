package app.aaps.database

import app.aaps.database.daos.APSResultDao
import app.aaps.database.daos.BolusCalculatorResultDao
import app.aaps.database.daos.BolusDao
import app.aaps.database.daos.CarbsDao
import app.aaps.database.daos.DeviceStatusDao
import app.aaps.database.daos.EffectiveProfileSwitchDao
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.daos.FoodDao
import app.aaps.database.daos.GlucoseValueDao
import app.aaps.database.daos.HeartRateDao
import app.aaps.database.daos.PreferenceChangeDao
import app.aaps.database.daos.ProfileSwitchDao
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.daos.StepsCountDao
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.daos.TotalDailyDoseDao
import app.aaps.database.daos.UserEntryDao
import app.aaps.database.daos.VersionChangeDao
import app.aaps.database.daos.delegated.DelegatedAPSResultDao
import app.aaps.database.daos.delegated.DelegatedBolusCalculatorResultDao
import app.aaps.database.daos.delegated.DelegatedBolusDao
import app.aaps.database.daos.delegated.DelegatedCarbsDao
import app.aaps.database.daos.delegated.DelegatedDeviceStatusDao
import app.aaps.database.daos.delegated.DelegatedEffectiveProfileSwitchDao
import app.aaps.database.daos.delegated.DelegatedExtendedBolusDao
import app.aaps.database.daos.delegated.DelegatedFoodDao
import app.aaps.database.daos.delegated.DelegatedGlucoseValueDao
import app.aaps.database.daos.delegated.DelegatedHeartRateDao
import app.aaps.database.daos.delegated.DelegatedPreferenceChangeDao
import app.aaps.database.daos.delegated.DelegatedProfileSwitchDao
import app.aaps.database.daos.delegated.DelegatedRunningModeDao
import app.aaps.database.daos.delegated.DelegatedStepsCountDao
import app.aaps.database.daos.delegated.DelegatedTemporaryBasalDao
import app.aaps.database.daos.delegated.DelegatedTemporaryTargetDao
import app.aaps.database.daos.delegated.DelegatedTherapyEventDao
import app.aaps.database.daos.delegated.DelegatedTotalDailyDoseDao
import app.aaps.database.daos.delegated.DelegatedUserEntryDao
import app.aaps.database.daos.delegated.DelegatedVersionChangeDao
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedAppDatabase(val changes: MutableList<DBEntry>, val database: AppDatabase) {

    val glucoseValueDao: GlucoseValueDao = DelegatedGlucoseValueDao(changes, database.glucoseValueDao)
    val therapyEventDao: TherapyEventDao = DelegatedTherapyEventDao(changes, database.therapyEventDao)
    val temporaryBasalDao: TemporaryBasalDao = DelegatedTemporaryBasalDao(changes, database.temporaryBasalDao)
    val bolusDao: BolusDao = DelegatedBolusDao(changes, database.bolusDao)
    val extendedBolusDao: ExtendedBolusDao = DelegatedExtendedBolusDao(changes, database.extendedBolusDao)
    val totalDailyDoseDao: TotalDailyDoseDao = DelegatedTotalDailyDoseDao(changes, database.totalDailyDoseDao)
    val carbsDao: CarbsDao = DelegatedCarbsDao(changes, database.carbsDao)
    val temporaryTargetDao: TemporaryTargetDao = DelegatedTemporaryTargetDao(changes, database.temporaryTargetDao)
    val bolusCalculatorResultDao: BolusCalculatorResultDao = DelegatedBolusCalculatorResultDao(changes, database.bolusCalculatorResultDao)
    val effectiveProfileSwitchDao: EffectiveProfileSwitchDao = DelegatedEffectiveProfileSwitchDao(changes, database.effectiveProfileSwitchDao)
    val profileSwitchDao: ProfileSwitchDao = DelegatedProfileSwitchDao(changes, database.profileSwitchDao)
    val apsResultDao: APSResultDao = DelegatedAPSResultDao(changes, database.apsResultDao)
    val versionChangeDao: VersionChangeDao = DelegatedVersionChangeDao(changes, database.versionChangeDao)
    val userEntryDao: UserEntryDao = DelegatedUserEntryDao(changes, database.userEntryDao)
    val preferenceChangeDao: PreferenceChangeDao = DelegatedPreferenceChangeDao(changes, database.preferenceChangeDao)
    val foodDao: FoodDao = DelegatedFoodDao(changes, database.foodDao)
    val deviceStatusDao: DeviceStatusDao = DelegatedDeviceStatusDao(changes, database.deviceStatusDao)
    val runningModeDao: RunningModeDao = DelegatedRunningModeDao(changes, database.runningModeDao)
    val heartRateDao: HeartRateDao = DelegatedHeartRateDao(changes, database.heartRateDao)
    val stepsCountDao: StepsCountDao = DelegatedStepsCountDao(changes, database.stepsCountDao)
    fun clearAllTables() = database.clearAllTables()
}
