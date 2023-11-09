package info.nightscout.interfaces.stats

import org.json.JSONObject

data class IsfCalculation (
    val isfNormalTarget : Double,
    val isf : Double,
    val ratio : Double,
    val insulinDivisor: Int,
    val velocity: Double)
{
    fun putTo(profile: JSONObject) {
        profile.put("dynISFvelocity", velocity)
        profile.put("sensNormalTarget", isfNormalTarget)
        profile.put("variable_sens", isf)
        profile.put("insulinDivisor", insulinDivisor)
    }
}