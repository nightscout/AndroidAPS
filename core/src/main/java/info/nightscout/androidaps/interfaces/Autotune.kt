package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile

interface Autotune {

    fun aapsAutotune()
    var currentprofile: ATProfile?
    var tunedProfile: ATProfile?
    var result: String
    var calculationRunning: Boolean
    var lastRun: Long
    var lastNbDays: String
    var copyButtonVisibility: Int
    var profileSwitchButtonVisibility: Int
    var lastRunSuccess: Boolean
}