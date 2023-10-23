package app.aaps.core.utils.fabric

import com.google.firebase.installations.FirebaseInstallations

object InstanceId {
    var instanceId : String = ""

    init {
        FirebaseInstallations.getInstance().id.addOnCompleteListener {
            instanceId = it.result
        }
    }
}