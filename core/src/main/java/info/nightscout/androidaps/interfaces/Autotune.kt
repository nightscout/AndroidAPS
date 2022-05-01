package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile

interface Autotune {

    fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String = ""): String
    fun atLog(message: String)

    var lastRunSuccess: Boolean
}