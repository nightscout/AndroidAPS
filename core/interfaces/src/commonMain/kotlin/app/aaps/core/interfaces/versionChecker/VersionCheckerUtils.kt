package app.aaps.core.interfaces.versionChecker

import kotlinx.serialization.json.JsonObject

interface VersionCheckerUtils {

    fun triggerCheckVersion()
    fun versionDigits(versionString: String?): IntArray

    /**
     * The allowed versions from Firebase remote config (its "defs" value), in the same flat form as the bundled
     * `definition.json`. They win over the bundled file and over the file fetched from GitHub.
     */
    fun setRemoteDefinition(definition: JsonObject)
}
