package info.nightscout.androidaps.utils

import android.provider.Settings
import com.google.firebase.iid.FirebaseInstanceId
import info.nightscout.androidaps.R

object InstanceId {
    fun instanceId(): String {
        var id = FirebaseInstanceId.getInstance().id
        return id
    }
}