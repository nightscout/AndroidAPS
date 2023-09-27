package app.aaps.core.interfaces.versionChecker

interface VersionCheckerUtils {

    fun triggerCheckVersion()
    fun compareWithCurrentVersion(newVersion: String?, currentVersion: String)
    fun versionDigits(versionString: String?): IntArray
    fun findVersion(file: String?): String?

}