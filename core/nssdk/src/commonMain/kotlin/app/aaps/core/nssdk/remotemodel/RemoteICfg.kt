package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Insulin configuration attached to a treatment.
 *
 * Defaults are here for the same reason as in [RemoteFood]: a non-null field without a default is
 * **mandatory** for kotlinx, and this is the only strict nested object on the treatments feed. Only
 * a present-but-partial `icfg` would throw today, and nothing writes one - but a treatments page is
 * decoded in a single pass, so if a future version ever adds a fifth field, every older reader would
 * lose the whole page rather than one record. The defaults cost nothing and remove that trap.
 */
@Serializable
data class RemoteICfg(
    @SerialName("insulinLabel") val insulinLabel: String = "",
    @SerialName("insulinEndTime") val insulinEndTime: Long = 0,
    @SerialName("insulinPeakTime") val insulinPeakTime: Long = 0,
    @SerialName("concentration") val concentration: Double = 0.0
)