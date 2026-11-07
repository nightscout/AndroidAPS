package app.aaps.plugins.libre3.crypto

/**
 * Android source: the classpath, exactly as the driver has always read these files. The same read
 * works from a plain host test, which is what lets the tables be checked without a device.
 */
internal actual fun libre3ReadTable(path: String): ByteArray? =
    Libre3RuntimeTables::class.java.getResourceAsStream(path)?.use { it.readBytes() }

internal actual fun libre3HasTable(path: String): Boolean =
    Libre3RuntimeTables::class.java.getResource(path) != null
