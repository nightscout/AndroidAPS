package app.aaps.plugins.sync.openhumans.delegates

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.sync.openhumans.OpenHumansState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHStateDelegate @Inject internal constructor(
    private val sp: SP
) {

    private var _value = MutableLiveData(loadState())
    val value = _value as LiveData<OpenHumansState?>

    private fun loadState(): OpenHumansState? {
        return OpenHumansState(
            accessToken = sp.getStringOrNull("openhumans_access_token", null) ?: return null,
            refreshToken = sp.getStringOrNull("openhumans_refresh_token", null) ?: return null,
            expiresAt = if (sp.contains("openhumans_expires_at"))
                sp.getLong("openhumans_expires_at", 0)
            else
                return null,
            projectMemberId = sp.getStringOrNull("openhumans_project_member_id", null)
                ?: return null,
            uploadOffset = sp.getLong("openhumans_upload_offset", 0)
        )
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): OpenHumansState? = _value.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: OpenHumansState?) {
        this._value.value = value
        if (value == null) {
            sp.remove("openhumans_access_token")
            sp.remove("openhumans_refresh_token")
            sp.remove("openhumans_expires_at")
            sp.remove("openhumans_project_member_id")
            sp.remove("openhumans_upload_offset")
        } else {
            sp.putString("openhumans_access_token", value.accessToken)
            sp.putString("openhumans_refresh_token", value.refreshToken)
            sp.putLong("openhumans_expires_at", value.expiresAt)
            sp.putString("openhumans_project_member_id", value.projectMemberId)
            sp.putLong("openhumans_upload_offset", value.uploadOffset)
        }
    }
}