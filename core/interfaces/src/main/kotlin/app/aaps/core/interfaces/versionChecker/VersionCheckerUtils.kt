package app.aaps.core.interfaces.versionChecker

interface VersionCheckerUtils {

    fun triggerCheckVersion()
    fun compareWithCurrentVersion(newVersion: String?, currentVersion: String): Boolean
    fun versionDigits(versionString: String?): IntArray
}