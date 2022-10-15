package info.nightscout.androidaps.utils

import com.google.firebase.installations.FirebaseInstallations

object InstanceId {
    var instanceId : String = ""

    init {
        FirebaseInstallations.getInstance().id.addOnCompleteListener {
            instanceId = it.result
        }
    }
}