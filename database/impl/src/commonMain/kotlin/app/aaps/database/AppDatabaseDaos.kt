package app.aaps.database

import app.aaps.database.daos.APSResultDao
import app.aaps.database.daos.BolusCalculatorResultDao
import app.aaps.database.daos.BolusDao
import app.aaps.database.daos.CalibrationEntryDao
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

/**
 * Every DAO the database exposes, without the Room class that carries them.
 *
 * [DelegatedAppDatabase] and so every transaction only ever reads DAOs off the database. Taking this
 * interface instead of [AppDatabase] keeps that whole layer free of `RoomDatabase`, which is what lets
 * it live beside code that has no Android in it.
 */
internal interface AppDatabaseDaos {

    val glucoseValueDao: GlucoseValueDao
    val therapyEventDao: TherapyEventDao
    val temporaryBasalDao: TemporaryBasalDao
    val bolusDao: BolusDao
    val extendedBolusDao: ExtendedBolusDao
    val totalDailyDoseDao: TotalDailyDoseDao
    val carbsDao: CarbsDao
    val temporaryTargetDao: TemporaryTargetDao
    val bolusCalculatorResultDao: BolusCalculatorResultDao
    val effectiveProfileSwitchDao: EffectiveProfileSwitchDao
    val profileSwitchDao: ProfileSwitchDao
    val apsResultDao: APSResultDao
    val versionChangeDao: VersionChangeDao
    val userEntryDao: UserEntryDao
    val preferenceChangeDao: PreferenceChangeDao
    val foodDao: FoodDao
    val deviceStatusDao: DeviceStatusDao
    val runningModeDao: RunningModeDao
    val heartRateDao: HeartRateDao
    val stepsCountDao: StepsCountDao
    val calibrationEntryDao: CalibrationEntryDao
}
