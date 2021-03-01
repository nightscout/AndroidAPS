package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class CharacteristicNotFoundException(cmdCharacteristicUuid: String) : FailedToConnectException("characteristic not found: $cmdCharacteristicUuid")
