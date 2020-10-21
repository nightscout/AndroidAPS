package info.nightscout.androidaps.utils.textValidator.validators

import android.util.Patterns

/**
 * Validates the ipaddress. The regexp was taken from the android source code.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class IpAddressValidator(_customErrorMessage: String?) : PatternValidator(_customErrorMessage, Patterns.IP_ADDRESS) 