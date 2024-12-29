package app.aaps.core.interfaces.aps

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class GlucoseStatus(
    val glucose: Double,
    val noise: Double = 0.0,
    val delta: Double = 0.0,
    val shortAvgDelta: Double = 0.0,
    val longAvgDelta: Double = 0.0,
    val date: Long = 0L,
    /** past duration of BG changing only within +/- 5% */
    val duraISFminutes: Double = 0.0,
    /** average BG while BG was changing less than +/- 5% */
    val duraISFaverage: Double = 0.0,
    /** past duration of BG approximating parabola shape */
    val parabolaMinutes: Double = 0.0,
    /** parabola derived last delta, i.e. from -5m to now */
    val deltaPl: Double = 0.0,
    /** parabola derived next delta, i.e. from now to 5m into the future */
    val deltaPn: Double = 0.0,
    /** parabola derived current BG acceleration */
    val bgAcceleration: Double = 0.0,
    /** coefficient a0 in approximation parabola formula
     *  BG = a0 + a1 * Time + a2 * Time^2
     *  where 1 unit of Time is 5 mibutes */
    val a0: Double = 0.0,
    /** coefficient a1 in approximation parabola formula
     *  BG = a0 + a1 * Time + a2 * Time^2
     *  where 1 unit of Time is 5 minutes */
    val a1: Double = 0.0,
    /** coefficient a2 in approximation parabola formula
     *  BG = a0 + a1 * Time + a2 * Time^2
     *  where 1 unit of Time is 5 minutes */
    val a2: Double = 0.0,
    /** correlation coefficient, i.e. quality of parabola fit */
    val corrSqu: Double = 0.0
)