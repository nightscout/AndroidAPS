package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.TABLE_MEAL_LINKS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry

@Entity(tableName = TABLE_MEAL_LINKS,
        foreignKeys = [ForeignKey(
                entity = Bolus::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("bolusId")), ForeignKey(

                entity = Carbs::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("carbsId")), ForeignKey(

                entity = BolusCalculatorResult::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("bolusCalcResultId")), ForeignKey(

                entity = TemporaryBasal::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("superbolusTempBasalId")), ForeignKey(

                entity = TherapyEvent::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("noteId")), ForeignKey(

                entity = MealLink::class,
                parentColumns = ["id"],
                childColumns = ["referenceId"])],
        indices = [Index("referenceId"), Index("bolusId"),
            Index("carbsId"), Index("bolusCalcResultId"),
            Index("superbolusTempBasalId"), Index("noteId")])
data class MealLink(
        @PrimaryKey(autoGenerate = true)
        override var id: Long = 0,
        override var version: Int = 0,
        override var dateCreated: Long = -1,
        override var isValid: Boolean = true,
        override var referenceId: Long? = null,
        @Embedded
        override var interfaceIDs_backing: InterfaceIDs? = null,
        var bolusId: Long? = null,
        var carbsId: Long? = null,
        var bolusCalcResultId: Long? = null,
        var superbolusTempBasalId: Long? = null,
        var noteId: Long? = null
) : TraceableDBEntry {
    override val foreignKeysValid: Boolean
        get() = super.foreignKeysValid && bolusId != 0L && carbsId != 0L &&
                bolusCalcResultId != 0L && superbolusTempBasalId != 0L && noteId != 0L
}