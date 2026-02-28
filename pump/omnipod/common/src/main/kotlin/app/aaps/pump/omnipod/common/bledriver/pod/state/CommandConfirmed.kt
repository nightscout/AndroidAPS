package app.aaps.pump.omnipod.common.bledriver.pod.state

data class CommandConfirmed(val command: OmnipodDashPodStateManager.ActiveCommand, val success: Boolean)
