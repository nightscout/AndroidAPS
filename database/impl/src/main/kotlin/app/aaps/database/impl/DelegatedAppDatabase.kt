package app.aaps.database.impl

import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.APSResultDao
import app.aaps.database.impl.daos.APSResultLinkDao
import app.aaps.database.impl.daos.BolusCalculatorResultDao
import app.aaps.database.impl.daos.BolusDao
import app.aaps.database.impl.daos.CarbsDao
import app.aaps.database.impl.daos.DeviceStatusDao
import app.aaps.database.impl.daos.EffectiveProfileSwitchDao
import app.aaps.database.impl.daos.ExtendedBolusDao
import app.aaps.database.impl.daos.FoodDao
import app.aaps.database.impl.daos.GlucoseValueDao
import app.aaps.database.impl.daos.HeartRateDao
import app.aaps.database.impl.daos.MultiwaveBolusLinkDao
import app.aaps.database.impl.daos.OfflineEventDao
import app.aaps.database.impl.daos.PreferenceChangeDao
import app.aaps.database.impl.daos.ProfileSwitchDao
import app.aaps.database.impl.daos.TemporaryBasalDao
import app.aaps.database.impl.daos.TemporaryTargetDao
import app.aaps.database.impl.daos.TherapyEventDao
import app.aaps.database.impl.daos.TotalDailyDoseDao
import app.aaps.database.impl.daos.UserEntryDao
import app.aaps.database.impl.daos.VersionChangeDao
import app.aaps.database.impl.daos.delegated.DelegatedAPSResultDao
import app.aaps.database.impl.daos.delegated.DelegatedAPSResultLinkDao
import app.aaps.database.impl.daos.delegated.DelegatedBolusCalculatorResultDao
import app.aaps.database.impl.daos.delegated.DelegatedBolusDao
import app.aaps.database.impl.daos.delegated.DelegatedCarbsDao
import app.aaps.database.impl.daos.delegated.DelegatedDeviceStatusDao
import app.aaps.database.impl.daos.delegated.DelegatedEffectiveProfileSwitchDao
import app.aaps.database.impl.daos.delegated.DelegatedExtendedBolusDao
import app.aaps.database.impl.daos.delegated.DelegatedFoodDao
import app.aaps.database.impl.daos.delegated.DelegatedGlucoseValueDao
import app.aaps.database.impl.daos.delegated.DelegatedHeartRateDao
import app.aaps.database.impl.daos.delegated.DelegatedMultiwaveBolusLinkDao
import app.aaps.database.impl.daos.delegated.DelegatedOfflineEventDao
import app.aaps.database.impl.daos.delegated.DelegatedPreferenceChangeDao
import app.aaps.database.impl.daos.delegated.DelegatedProfileSwitchDao
import app.aaps.database.impl.daos.delegated.DelegatedTemporaryBasalDao
import app.aaps.database.impl.daos.delegated.DelegatedTemporaryTargetDao
import app.aaps.database.impl.daos.delegated.DelegatedTherapyEventDao
import app.aaps.database.impl.daos.delegated.DelegatedTotalDailyDoseDao
import app.aaps.database.impl.daos.delegated.DelegatedUserEntryDao
import app.aaps.database.impl.daos.delegated.DelegatedVersionChangeDao

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
