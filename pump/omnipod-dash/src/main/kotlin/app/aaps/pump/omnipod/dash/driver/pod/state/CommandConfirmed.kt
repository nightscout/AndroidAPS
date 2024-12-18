package app.aaps.pump.omnipod.dash.driver.pod.state

data class CommandConfirmed(val command: OmnipodDashPodStateManager.ActiveCommand, val success: Boolean)
