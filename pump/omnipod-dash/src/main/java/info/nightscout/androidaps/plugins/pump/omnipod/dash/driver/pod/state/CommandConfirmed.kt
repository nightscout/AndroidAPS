package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

data class CommandConfirmed(val command: OmnipodDashPodStateManager.ActiveCommand, val success: Boolean)
