package app.aaps.core.interfaces.versionChecker

import org.json.JSONObject

fun interface VersionDefinition {

    fun invoke(): JSONObject
}