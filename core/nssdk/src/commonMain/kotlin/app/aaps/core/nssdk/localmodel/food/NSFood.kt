package app.aaps.core.nssdk.localmodel.food

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import kotlinx.serialization.Serializable

@Serializable
data class NSFood(
    val date: Long,
    val device: String? = null,
    val identifier: String?,
    val units: NsUnits? = null,
    val srvModified: Long? = null,
    val srvCreated: Long? = null,
    val subject: String? = null,
    var isReadOnly: Boolean = false,
    val isValid: Boolean,
    var app: String? = null,
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
)
