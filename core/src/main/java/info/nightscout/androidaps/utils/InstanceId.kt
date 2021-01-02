package info.nightscout.androidaps.utils

import com.google.firebase.iid.FirebaseInstanceId

object InstanceId {
    fun instanceId(): String {
        val id = FirebaseInstanceId.getInstance().id
        return id
    }
}