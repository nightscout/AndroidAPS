package app.aaps.plugins.sync.openhumans.delegates

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.keys.Preferences
import app.aaps.plugins.sync.openhumans.OpenHumansState
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
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
            accessToken = preferences.getIfExists(OpenHumansUploaderPlugin.OhStringKey.AccessToken) ?: return null,
            refreshToken = preferences.getIfExists(OpenHumansUploaderPlugin.OhStringKey.RefreshToken) ?: return null,
            expiresAt = preferences.getIfExists(OpenHumansUploaderPlugin.OhLongKey.ExpiresAt) ?: return null,
            projectMemberId = preferences.getIfExists(OpenHumansUploaderPlugin.OhStringKey.ProjectMemberId) ?: return null,
            uploadOffset = preferences.get(OpenHumansUploaderPlugin.OhLongKey.UploadOffset)
        )
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): OpenHumansState? = _value.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: OpenHumansState?) {
        this._value.value = value
        if (value == null) {
            preferences.remove(OpenHumansUploaderPlugin.OhStringKey.AccessToken)
            preferences.remove(OpenHumansUploaderPlugin.OhStringKey.RefreshToken)
            preferences.remove(OpenHumansUploaderPlugin.OhLongKey.ExpiresAt)
            preferences.remove(OpenHumansUploaderPlugin.OhStringKey.ProjectMemberId)
            preferences.remove(OpenHumansUploaderPlugin.OhLongKey.UploadOffset)
        } else {
            preferences.put(OpenHumansUploaderPlugin.OhStringKey.AccessToken, value.accessToken)
            preferences.put(OpenHumansUploaderPlugin.OhStringKey.RefreshToken, value.refreshToken)
            preferences.put(OpenHumansUploaderPlugin.OhLongKey.ExpiresAt, value.expiresAt)
            preferences.put(OpenHumansUploaderPlugin.OhStringKey.ProjectMemberId, value.projectMemberId)
            preferences.put(OpenHumansUploaderPlugin.OhLongKey.UploadOffset, value.uploadOffset)
        }
    }
}