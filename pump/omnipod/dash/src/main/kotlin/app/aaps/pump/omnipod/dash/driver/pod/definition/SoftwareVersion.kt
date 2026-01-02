package app.aaps.pump.omnipod.dash.driver.pod.definition

import java.io.Serializable

data class SoftwareVersion(
    private val major: Short,
    private val minor: Short,
    private val interim: Short
) : Serializable {

    override fun toString(): String {
        return "$major.$minor.$interim"
    }
}
