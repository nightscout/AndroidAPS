package app.aaps.plugins.sync.openhumans.delegates

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.openhumans.OpenHumansState
import app.aaps.plugins.sync.openhumans.keys.OhLongKey
import app.aaps.plugins.sync.openhumans.keys.OhStringKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHStateDelegate @Inject internal constructor(
    private val preferences: Preferences
) {

    private var _value = MutableLiveData(loadState())
    val value = _value as LiveData<OpenHumansState?>

    private fun loadState(): OpenHumansState? {
        return OpenHumansState(
            accessToken = preferences.getIfExists(OhStringKey.AccessToken) ?: return null,
            refreshToken = preferences.getIfExists(OhStringKey.RefreshToken) ?: return null,
            expiresAt = preferences.getIfExists(OhLongKey.ExpiresAt) ?: return null,
            projectMemberId = preferences.getIfExists(OhStringKey.ProjectMemberId) ?: return null,
            uploadOffset = preferences.get(OhLongKey.UploadOffset)
        )
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): OpenHumansState? = _value.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: OpenHumansState?) {
        this._value.value = value
        if (value == null) {
            preferences.remove(OhStringKey.AccessToken)
            preferences.remove(OhStringKey.RefreshToken)
            preferences.remove(OhLongKey.ExpiresAt)
            preferences.remove(OhStringKey.ProjectMemberId)
            preferences.remove(OhLongKey.UploadOffset)
        } else {
            preferences.put(OhStringKey.AccessToken, value.accessToken)
            preferences.put(OhStringKey.RefreshToken, value.refreshToken)
            preferences.put(OhLongKey.ExpiresAt, value.expiresAt)
            preferences.put(OhStringKey.ProjectMemberId, value.projectMemberId)
            preferences.put(OhLongKey.UploadOffset, value.uploadOffset)
        }
    }
}