package info.nightscout.androidaps.utils

import com.google.firebase.iid.FirebaseInstanceId

object InstanceId {
    fun instanceId(): String {
        var id = FirebaseInstanceId.getInstance().id
        return id
    }
}