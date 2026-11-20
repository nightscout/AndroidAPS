package app.aaps.plugins.libre3.crypto

/**
 * Reads the bytes of one fixed table that ships with the app.
 *
 * @param path the full path of the file, starting with a slash, as [Libre3RuntimeTables] builds it.
 * @return the bytes, or null when this build does not carry that file.
 *
 * ⚠️ Null means "this part of the driver cannot run". A platform must never make bytes up here:
 * a wrong table would not fail, it would pair wrongly.
 */
internal expect fun libre3ReadTable(path: String): ByteArray?

/**
 * True when the file at [path] really ships with this build.
 *
 * This must not read the bytes. Some of these files are hundreds of kilobytes and the answer is
 * needed on every session start.
 */
internal expect fun libre3HasTable(path: String): Boolean
