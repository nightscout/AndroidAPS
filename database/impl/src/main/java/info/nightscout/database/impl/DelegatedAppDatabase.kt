package info.nightscout.database.impl

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
import info.nightscout.database.impl.daos.delegated.DelegatedAPSResultDao
import info.nightscout.database.impl.daos.delegated.DelegatedAPSResultLinkDao
import info.nightscout.database.impl.daos.delegated.DelegatedBolusCalculatorResultDao
import info.nightscout.database.impl.daos.delegated.DelegatedBolusDao
import info.nightscout.database.impl.daos.delegated.DelegatedCarbsDao
import info.nightscout.database.impl.daos.delegated.DelegatedDeviceStatusDao
import info.nightscout.database.impl.daos.delegated.DelegatedEffectiveProfileSwitchDao
import info.nightscout.database.impl.daos.delegated.DelegatedExtendedBolusDao
import info.nightscout.database.impl.daos.delegated.DelegatedFoodDao
import info.nightscout.database.impl.daos.delegated.DelegatedGlucoseValueDao
import info.nightscout.database.impl.daos.delegated.DelegatedMultiwaveBolusLinkDao
import info.nightscout.database.impl.daos.delegated.DelegatedOfflineEventDao
import info.nightscout.database.impl.daos.delegated.DelegatedPreferenceChangeDao
import info.nightscout.database.impl.daos.delegated.DelegatedProfileSwitchDao
import info.nightscout.database.impl.daos.delegated.DelegatedTemporaryBasalDao
import info.nightscout.database.impl.daos.delegated.DelegatedTemporaryTargetDao
import info.nightscout.database.impl.daos.delegated.DelegatedTherapyEventDao
import info.nightscout.database.impl.daos.delegated.DelegatedTotalDailyDoseDao
import info.nightscout.database.impl.daos.delegated.DelegatedUserEntryDao
import info.nightscout.database.impl.daos.delegated.DelegatedVersionChangeDao
import info.nightscout.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.HeartRateDao
import info.nightscout.database.impl.daos.delegated.DelegatedHeartRateDao

internal class DelegatedAppDatabase(val changes: MutableList<DBEntry>, val database: AppDatabase) {

    val glucoseValueDao: GlucoseValueDao = DelegatedGlucoseValueDao(changes, database.glucoseValueDao)
    val therapyEventDao: TherapyEventDao = DelegatedTherapyEventDao(changes, database.therapyEventDao)
    val temporaryBasalDao: TemporaryBasalDao = DelegatedTemporaryBasalDao(changes, database.temporaryBasalDao)
    val bolusDao: BolusDao = DelegatedBolusDao(changes, database.bolusDao)
    val extendedBolusDao: ExtendedBolusDao = DelegatedExtendedBolusDao(changes, database.extendedBolusDao)
    val multiwaveBolusLinkDao: MultiwaveBolusLinkDao = DelegatedMultiwaveBolusLinkDao(changes, database.multiwaveBolusLinkDao)
    val totalDailyDoseDao: TotalDailyDoseDao = DelegatedTotalDailyDoseDao(changes, database.totalDailyDoseDao)
    val carbsDao: CarbsDao = DelegatedCarbsDao(changes, database.carbsDao)
    val temporaryTargetDao: TemporaryTargetDao = DelegatedTemporaryTargetDao(changes, database.temporaryTargetDao)
    val apsResultLinkDao: APSResultLinkDao = DelegatedAPSResultLinkDao(changes, database.apsResultLinkDao)
    val bolusCalculatorResultDao: BolusCalculatorResultDao = DelegatedBolusCalculatorResultDao(changes, database.bolusCalculatorResultDao)
    val effectiveProfileSwitchDao: EffectiveProfileSwitchDao = DelegatedEffectiveProfileSwitchDao(changes, database.effectiveProfileSwitchDao)
    val profileSwitchDao: ProfileSwitchDao = DelegatedProfileSwitchDao(changes, database.profileSwitchDao)
    val apsResultDao: APSResultDao = DelegatedAPSResultDao(changes, database.apsResultDao)
    val versionChangeDao: VersionChangeDao = DelegatedVersionChangeDao(changes, database.versionChangeDao)
    val userEntryDao: UserEntryDao = DelegatedUserEntryDao(changes, database.userEntryDao)
    val preferenceChangeDao: PreferenceChangeDao = DelegatedPreferenceChangeDao(changes, database.preferenceChangeDao)
    val foodDao: FoodDao = DelegatedFoodDao(changes, database.foodDao)
    val deviceStatusDao: DeviceStatusDao = DelegatedDeviceStatusDao(changes, database.deviceStatusDao)
    val offlineEventDao: OfflineEventDao = DelegatedOfflineEventDao(changes, database.offlineEventDao)
    val heartRateDao: HeartRateDao = DelegatedHeartRateDao(changes, database.heartRateDao)
    fun clearAllTables() = database.clearAllTables()
}
