package app.aaps.plugins.sync.openhumans.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class OhStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    AppId("openhumans_appid", ""),
    AccessToken("openhumans_access_token", ""),
    RefreshToken("openhumans_refresh_token", ""),
    ProjectMemberId("openhumans_project_member_id", ""),
}