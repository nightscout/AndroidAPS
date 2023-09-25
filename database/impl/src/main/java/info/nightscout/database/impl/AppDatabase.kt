package info.nightscout.database.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
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
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.VersionChange
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
import info.nightscout.database.impl.daos.HeartRateDao
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
import java.io.Closeable

const val DATABASE_VERSION = 24

@Database(
    version = DATABASE_VERSION,
    entities = [app.aaps.database.entities.APSResult::class, Bolus::class, BolusCalculatorResult::class, Carbs::class,
        EffectiveProfileSwitch::class, ExtendedBolus::class, GlucoseValue::class, ProfileSwitch::class,
        TemporaryBasal::class, TemporaryTarget::class, TherapyEvent::class, TotalDailyDose::class, app.aaps.database.entities.APSResultLink::class,
        MultiwaveBolusLink::class, PreferenceChange::class, VersionChange::class, UserEntry::class,
        Food::class, DeviceStatus::class, OfflineEvent::class, HeartRate::class],
    exportSchema = true
)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : Closeable, RoomDatabase() {

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

    abstract val heartRateDao: HeartRateDao
}
