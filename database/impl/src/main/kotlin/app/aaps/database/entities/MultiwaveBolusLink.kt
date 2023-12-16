package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.TraceableDBEntry

@Entity(
    tableName = TABLE_MULTIWAVE_BOLUS_LINKS,
    foreignKeys = [ForeignKey(
        entity = Bolus::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("bolusId")
    ), ForeignKey(

        entity = ExtendedBolus::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("extendedBolusId")
    ), ForeignKey(

        entity = MultiwaveBolusLink::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [Index("referenceId"), Index("bolusId"),
        Index("extendedBolusId")]
)
data class MultiwaveBolusLink(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null,
    var bolusId: Long,
    var extendedBolusId: Long
) : TraceableDBEntry {

    override val foreignKeysValid: Boolean
        get() = super.foreignKeysValid && bolusId != 0L && bolusId != 0L && extendedBolusId != 0L
}