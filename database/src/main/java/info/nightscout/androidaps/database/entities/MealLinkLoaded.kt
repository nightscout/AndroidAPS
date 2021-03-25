package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Relation

data class MealLinkLoaded constructor(
    @Embedded val mealLink: MealLink,
    @Relation(
        entity = Bolus::class,
        parentColumn = "bolusId",
        entityColumn = "id")
    val bolus: Bolus?,
    @Relation(
        entity = Carbs::class,
        parentColumn = "carbsId",
        entityColumn = "id")
    val carbs: Carbs?,
    @Relation(
        entity = BolusCalculatorResult::class,
        parentColumn = "bolusCalcResultId",
        entityColumn = "id")
    val bolusCalculatorResult: BolusCalculatorResult?,
    @Relation(
        entity = TemporaryBasal::class,
        parentColumn = "superbolusTempBasalId",
        entityColumn = "id")
    val superBolusTemporaryBasal: TemporaryBasal?,
    @Relation(
        entity = TherapyEvent::class,
        parentColumn = "noteId",
        entityColumn = "id")
    val therapyEvent: TherapyEvent?
)