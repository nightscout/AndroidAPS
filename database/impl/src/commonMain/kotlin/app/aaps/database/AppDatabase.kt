package app.aaps.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
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
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.CalibrationEntry
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

const val DATABASE_VERSION = 35

@Database(
    version = DATABASE_VERSION,
    entities = [app.aaps.database.entities.APSResult::class, Bolus::class, BolusCalculatorResult::class, Carbs::class,
        EffectiveProfileSwitch::class, ExtendedBolus::class, GlucoseValue::class, ProfileSwitch::class,
        TemporaryBasal::class, TemporaryTarget::class, TherapyEvent::class, TotalDailyDose::class,
        PreferenceChange::class, VersionChange::class, UserEntry::class,
        Food::class, DeviceStatus::class, RunningMode::class, HeartRate::class, StepsCount::class,
        CalibrationEntry::class],
    exportSchema = true
)
// @ConstructedBy is what lets this class live in commonMain: Room generates the initialiser per target
// and the expect object below is where each one lands. On Android alone it would be optional.
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase(), AppDatabaseDaos {

    abstract override val glucoseValueDao: GlucoseValueDao

    abstract override val therapyEventDao: TherapyEventDao

    abstract override val temporaryBasalDao: TemporaryBasalDao

    abstract override val bolusDao: BolusDao

    abstract override val extendedBolusDao: ExtendedBolusDao

    abstract override val totalDailyDoseDao: TotalDailyDoseDao

    abstract override val carbsDao: CarbsDao

    abstract override val temporaryTargetDao: TemporaryTargetDao

    abstract override val bolusCalculatorResultDao: BolusCalculatorResultDao

    abstract override val effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    abstract override val profileSwitchDao: ProfileSwitchDao

    abstract override val apsResultDao: APSResultDao

    abstract override val versionChangeDao: VersionChangeDao

    abstract override val userEntryDao: UserEntryDao

    abstract override val preferenceChangeDao: PreferenceChangeDao

    abstract override val foodDao: FoodDao

    abstract override val deviceStatusDao: DeviceStatusDao

    abstract override val runningModeDao: RunningModeDao

    abstract override val heartRateDao: HeartRateDao

    abstract override val stepsCountDao: StepsCountDao

    abstract override val calibrationEntryDao: CalibrationEntryDao
}

// Room supplies the actual per target. It must be `expect`, because the generated initialiser is
// platform specific - that is the whole reason @ConstructedBy exists.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {

    override fun initialize(): AppDatabase
}
