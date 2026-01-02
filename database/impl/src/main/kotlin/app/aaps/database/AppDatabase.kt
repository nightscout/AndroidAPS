package app.aaps.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.PreferenceChange
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.VersionChange

const val DATABASE_VERSION = 31

@Database(
    version = DATABASE_VERSION,
    entities = [app.aaps.database.entities.APSResult::class, Bolus::class, BolusCalculatorResult::class, Carbs::class,
        EffectiveProfileSwitch::class, ExtendedBolus::class, GlucoseValue::class, ProfileSwitch::class,
        TemporaryBasal::class, TemporaryTarget::class, TherapyEvent::class, TotalDailyDose::class,
        PreferenceChange::class, VersionChange::class, UserEntry::class,
        Food::class, DeviceStatus::class, RunningMode::class, HeartRate::class, StepsCount::class],
    exportSchema = true
)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {

    abstract val glucoseValueDao: GlucoseValueDao

    abstract val therapyEventDao: TherapyEventDao

    abstract val temporaryBasalDao: TemporaryBasalDao

    abstract val bolusDao: BolusDao

    abstract val extendedBolusDao: ExtendedBolusDao

    abstract val totalDailyDoseDao: TotalDailyDoseDao

    abstract val carbsDao: CarbsDao

    abstract val temporaryTargetDao: TemporaryTargetDao

    abstract val bolusCalculatorResultDao: BolusCalculatorResultDao

    abstract val effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    abstract val profileSwitchDao: ProfileSwitchDao

    abstract val apsResultDao: APSResultDao

    abstract val versionChangeDao: VersionChangeDao

    abstract val userEntryDao: UserEntryDao

    abstract val preferenceChangeDao: PreferenceChangeDao

    abstract val foodDao: FoodDao

    abstract val deviceStatusDao: DeviceStatusDao

    abstract val runningModeDao: RunningModeDao

    abstract val heartRateDao: HeartRateDao

    abstract val stepsCountDao: StepsCountDao
}
