package app.aaps.core.nssdk.interfaces

import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import kotlinx.serialization.json.JsonObject

interface RunningConfiguration {

    // called in AAPS mode only — "cold" settings doc: plugin config that changes only on user edits.
    fun configuration(): JsonObject

    // called in AAPS mode only — "hot" settings doc: the active scene and the "time to eat" timer, which change
    // more often than the cold settings. Published to a separate identifier.
    fun hotConfiguration(): JsonObject

    // called in NSClient mode only — apply the cold doc (everything except the hot state).
    fun applyCold(configuration: NSRunningConfiguration)

    // called in NSClient mode only — apply the hot doc (the active scene and the "time to eat" timer).
    fun applyHot(configuration: NSRunningConfiguration)
}
