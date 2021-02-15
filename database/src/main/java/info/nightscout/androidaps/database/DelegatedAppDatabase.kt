package info.nightscout.androidaps.database

import info.nightscout.androidaps.database.daos.*
import info.nightscout.androidaps.database.daos.delegated.*
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedAppDatabase(val changes: MutableList<DBEntry>, val database: AppDatabase) {

    val glucoseValueDao: GlucoseValueDao = DelegatedGlucoseValueDao(changes, database.glucoseValueDao)
    val therapyEventDao: TherapyEventDao = DelegatedTherapyEventDao(changes, database.therapyEventDao)
    val temporaryBasalDao: TemporaryBasalDao = DelegatedTemporaryBasalDao(changes, database.temporaryBasalDao)
    val bolusDao: BolusDao = DelegatedBolusDao(changes, database.bolusDao)
    val extendedBolusDao: ExtendedBolusDao = DelegatedExtendedExtendedBolusDao(changes, database.extendedBolusDao)
    val multiwaveBolusLinkDao: MultiwaveBolusLinkDao = DelegatedMultiwaveBolusLinkDao(changes, database.multiwaveBolusLinkDao)
    val totalDailyDoseDao: TotalDailyDoseDao = DelegatedTotalDailyDoseDao(changes, database.totalDailyDoseDao)
    val carbsDao: CarbsDao = DelegatedCarbsDao(changes, database.carbsDao)
    val mealLinkDao: MealLinkDao = DelegatedMealLinkDao(changes, database.mealLinkDao)
    val temporaryTargetDao: TemporaryTargetDao = DelegatedTemporaryTargetDao(changes, database.temporaryTargetDao)
    val apsResultLinkDao: APSResultLinkDao = DelegatedAPSResultLinkLinkDao(changes, database.apsResultLinkDao)
    val bolusCalculatorResultDao: BolusCalculatorResultDao = DelegatedBolusCalculatorResultDao(changes, database.bolusCalculatorResultDao)
    val effectiveProfileSwitchDao: EffectiveProfileSwitchDao = DelegatedEffectiveProfileSwitchDao(changes, database.effectiveProfileSwitchDao)
    val profileSwitchDao: ProfileSwitchDao = DelegatedProfileSwitchDao(changes, database.profileSwitchDao)
    val apsResultDao: APSResultDao = DelegatedAPSResultDao(changes, database.apsResultDao)
    val versionChangeDao: VersionChangeDao = DelegatedVersionChangeDao(changes, database.versionChangeDao)
    val userEntryDao: UserEntryDao = DelegatedUserEntryDao(changes, database.userEntryDao)
    val preferenceChangeDao: PreferenceChangeDao = DelegatedPreferenceChangeDao(changes, database.preferenceChangeDao)
    fun clearAllTables() = database.clearAllTables()
}