package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

@Serializable
data class AutosensResult(

    //default values to show when autosens algorithm is not called
    var ratio: Double = 1.0,
    var carbsAbsorbed: Double = 0.0,
    var sensResult: String = "autosens not available",
    var pastSensitivity: String = "",
    var ratioLimit: String = "",
    // Dynamic sensitivity
    // insulin within last24h/average7days
    var ratioFromTdd: Double = 1.0,
    // carbs within last24h/average7days
    var ratioFromCarbs: Double = 1.0
)