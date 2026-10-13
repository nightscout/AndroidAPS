package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class TidepoolStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    SubscriptionId("tidepool_subscription_id", ""),
    AuthState("tidepool_auth_state", ""),
    ServiceConfiguration("tidepool_service_configuration", "")
}