package app.aaps.core.nssdk.interfaces

import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import org.json.JSONObject

interface RunningConfiguration {

    // called in AAPS mode only — "cold" settings doc: plugin config that changes only on user edits.
    fun configuration(): JSONObject

    // called in AAPS mode only — "hot" settings doc: runtime state that changes frequently
    // (active scene lifecycle) plus computed runtime flags. Published to a separate identifier.
    fun activeSceneConfiguration(): JSONObject

    // called in NSClient mode only — apply the cold doc (everything except the active scene).
    fun applyCold(configuration: NSRunningConfiguration)

    // called in NSClient mode only — apply the hot doc (active scene + computed runtime flags).
    fun applyHot(configuration: NSRunningConfiguration)
}
