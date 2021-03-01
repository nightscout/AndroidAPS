package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class ServiceNotFoundException(serviceUuid: String) : FailedToConnectException("service not found: $serviceUuid")
