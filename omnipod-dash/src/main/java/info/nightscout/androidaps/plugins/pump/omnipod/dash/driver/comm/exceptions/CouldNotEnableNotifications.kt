package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType

class CouldNotEnableNotifications(cmd: CharacteristicType) : Exception(cmd.value)