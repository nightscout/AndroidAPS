package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.TraceableDBEntry

@Entity(
    tableName = TABLE_FOODS,
    foreignKeys = [ForeignKey(
        entity = Food::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("referenceId"),
        Index("isValid")
    ]
)
data class Food(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null,
    var name: String,
    var category: String? = null,
    var subCategory: String? = null,
    // Example:
    // name="juice" portion=250 units="ml" carbs=12
    // means 250ml of juice has 12g of carbs

    var portion: Double, // common portion in "units"
    var carbs: Int, // in grams
    var fat: Int? = null, // in grams
    var protein: Int? = null, // in grams
    var energy: Int? = null, // in kJ
    var unit: String = "g",
    var gi: Int? = null // not used yet

) : TraceableDBEntry {

    fun contentEqualsTo(other: Food): Boolean {
        if (isValid != other.isValid) return false
        if (portion != other.portion) return false
        if (carbs != other.carbs) return false
        if (fat != other.fat) return false
        if (protein != other.protein) return false
        if (energy != other.energy) return false
        if (gi != other.gi) return false
        if (name != other.name) return false
        if (category != other.category) return false
        if (subCategory != other.subCategory) return false
        return unit == other.unit
    }

    fun onlyNsIdAdded(previous: Food): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    fun copyFrom(other: Food) {
        isValid = other.isValid
        name = other.name
        category = other.category
        subCategory = other.subCategory
        portion = other.portion
        carbs = other.carbs
        fat = other.fat
        protein = other.protein
        energy = other.energy
        unit = other.unit
        gi = other.gi
        interfaceIDs.nightscoutId = other.interfaceIDs.nightscoutId
    }

    companion object
}