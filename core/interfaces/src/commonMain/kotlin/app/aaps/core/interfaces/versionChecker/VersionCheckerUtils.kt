package app.aaps.core.interfaces.versionChecker

interface VersionCheckerUtils {

    fun triggerCheckVersion()
    fun versionDigits(versionString: String?): IntArray
}