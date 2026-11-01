package app.aaps.pump.omnipod.omnipod5.util

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanFailFoundTooManyException
import app.aaps.pump.omnipod.omnipod5.R

class I8n {
    companion object {

        fun textFromException(exception: Throwable, rs: ResourceHelper): String {
            return when (exception) {
                is FailedToConnectException      -> rs.gs(R.string.omnipod_o5_failed_to_connect)
                is ScanFailFoundTooManyException -> rs.gs(R.string.omnipod_o5_found_too_many_pods)
                is ScanException                 -> rs.gs(R.string.omnipod_o5_scan_failed)
                is NotConnectedException         -> rs.gs(R.string.omnipod_o5_connection_lost)
                else                             ->
                    rs.gs(R.string.omnipod_o5_generic_error, exception.toString())
            }
        }
    }
}
