package app.aaps.core.data.model

data class FD(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
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

) : HasIDs {

    fun contentEqualsTo(other: FD): Boolean {
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

    fun onlyNsIdAdded(previous: FD): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    companion object
}