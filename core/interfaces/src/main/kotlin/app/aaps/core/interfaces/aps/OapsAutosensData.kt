package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

@Serializable
data class OapsAutosensData(
    var ratio: Double
)