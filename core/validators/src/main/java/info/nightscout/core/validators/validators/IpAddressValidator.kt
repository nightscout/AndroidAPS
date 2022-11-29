package info.nightscout.core.validators.validators

import android.util.Patterns

/**
 * Validates the ipaddress. The regexp was taken from the android source code.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
@Suppress("deprecation")
class IpAddressValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.IP_ADDRESS)