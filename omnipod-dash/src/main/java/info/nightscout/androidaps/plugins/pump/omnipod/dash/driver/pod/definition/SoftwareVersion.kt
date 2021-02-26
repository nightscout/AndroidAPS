package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.io.Serializable

data class SoftwareVersion(private val major: Int, private val minor: Int, private val interim: Int) : Serializable
