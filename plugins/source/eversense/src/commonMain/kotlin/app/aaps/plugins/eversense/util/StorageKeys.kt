package app.aaps.plugins.eversense.util

class StorageKeys {
    companion object {
        const val REMOTE_DEVICE_KEY = "eversense_remote_device"

        /** The name the paired transmitter advertised, shown to the user ("T" and its serial number) */
        const val REMOTE_DEVICE_NAME = "eversense_remote_device_name"
        const val STATE = "eversense_state"
        const val SECURE_STATE = "eversense_state_secure"
        const val CLOUD_QUEUE = "eversense_cloud_queue"
        const val CLOUD_LAST_ATTEMPT = "eversense_cloud_last_attempt"
        const val ACCESS_TOKEN = "eversense_access_token"
        const val ACCESS_TOKEN_EXPIRY = "eversense_access_token_expiry"

        /** True after the Eversense server refused the stored login, until a login is accepted or the user signs out */
        const val LOGIN_REFUSED = "eversense_login_refused"

        /**
         * The name of a transmitter that was unpaired while the phone kept its Bluetooth bond (iOS, where an app cannot
         * remove one, or a failed removal on Android), until the next pairing: the user is told to forget it in the
         * phone's Bluetooth settings.
         */
        const val BOND_LEFT_NAME = "eversense_bond_left_name"
    }
}
