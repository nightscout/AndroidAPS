package info.nightscout.database.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import info.nightscout.database.impl.daos.APSResultDao
import info.nightscout.database.impl.daos.APSResultLinkDao
import info.nightscout.database.impl.daos.BolusCalculatorResultDao
import info.nightscout.database.impl.daos.BolusDao
import info.nightscout.database.impl.daos.CarbsDao
import info.nightscout.database.impl.daos.DeviceStatusDao
import info.nightscout.database.impl.daos.EffectiveProfileSwitchDao
import info.nightscout.database.impl.daos.ExtendedBolusDao
import info.nightscout.database.impl.daos.FoodDao
import info.nightscout.database.impl.daos.GlucoseValueDao
import info.nightscout.database.impl.daos.MultiwaveBolusLinkDao
import info.nightscout.database.impl.daos.OfflineEventDao
import info.nightscout.database.impl.daos.PreferenceChangeDao
import info.nightscout.database.impl.daos.ProfileSwitchDao
import info.nightscout.database.impl.daos.TemporaryBasalDao
import info.nightscout.database.impl.daos.TemporaryTargetDao
import info.nightscout.database.impl.daos.TherapyEventDao
import info.nightscout.database.impl.daos.TotalDailyDoseDao
import info.nightscout.database.impl.daos.UserEntryDao
import info.nightscout.database.impl.daos.VersionChangeDao
import info.nightscout.database.entities.APSResult
import info.nightscout.database.entities.APSResultLink
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.MultiwaveBolusLink
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.PreferenceChange
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.TotalDailyDose
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.VersionChange

const val DATABASE_VERSION = 23

@Database(version = DATABASE_VERSION,
          entities = [APSResult::class, Bolus::class, BolusCalculatorResult::class, Carbs::class,
        EffectiveProfileSwitch::class, ExtendedBolus::class, GlucoseValue::class, ProfileSwitch::class,
        TemporaryBasal::class, TemporaryTarget::class, TherapyEvent::class, TotalDailyDose::class, APSResultLink::class,
        MultiwaveBolusLink::class, PreferenceChange::class, VersionChange::class, UserEntry::class,
        Food::class, DeviceStatus::class, OfflineEvent::class],
          exportSchema = true)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {

    abstract val glucoseValueDao: GlucoseValueDao

    abstract val therapyEventDao: TherapyEventDao

    abstract val temporaryBasalDao: TemporaryBasalDao

    abstract val bolusDao: BolusDao

    abstract val extendedBolusDao: ExtendedBolusDao

    abstract val multiwaveBolusLinkDao: MultiwaveBolusLinkDao

    abstract val totalDailyDoseDao: TotalDailyDoseDao

    abstract val carbsDao: CarbsDao

    abstract val temporaryTargetDao: TemporaryTargetDao

    abstract val apsResultLinkDao: APSResultLinkDao

    abstract val bolusCalculatorResultDao: BolusCalculatorResultDao

    abstract val effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    abstract val profileSwitchDao: ProfileSwitchDao

    abstract val apsResultDao: APSResultDao

    abstract val versionChangeDao: VersionChangeDao

    abstract val userEntryDao: UserEntryDao

    abstract val preferenceChangeDao: PreferenceChangeDao

    abstract val foodDao: FoodDao

    abstract val deviceStatusDao: DeviceStatusDao

    abstract val offlineEventDao: OfflineEventDao

}