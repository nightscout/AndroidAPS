package app.aaps.shared.impl.weardata

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfFile
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.CwfMetadataMap
import app.aaps.core.interfaces.rx.weardata.CwfResDataMap
import app.aaps.core.interfaces.rx.weardata.ResData
import app.aaps.core.interfaces.rx.weardata.ResFormat
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipWatchfaceFormat {
    companion object {

        const val CWF_EXTENSION = "zip"
        private const val CWF_JSON_FILE = "CustomWatchface.json"

        fun loadCustomWatchface(byteArray: ByteArray, zipName: String, authorization: Boolean): CwfFile? {
            var json = JSONObject()
            var metadata: CwfMetadataMap = mutableMapOf()
            val resData: CwfResDataMap = mutableMapOf()
            val zipInputStream = byteArrayToZipInputStream(byteArray)
            try {
                var zipEntry: ZipEntry? = zipInputStream.nextEntry
                while (zipEntry != null) {
                    val entryName = zipEntry.name

                    val buffer = ByteArray(2048)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    var count = zipInputStream.read(buffer)
                    while (count != -1) {
                        byteArrayOutputStream.write(buffer, 0, count)
                        count = zipInputStream.read(buffer)
                    }
                    zipInputStream.closeEntry()

                    if (entryName == CWF_JSON_FILE) {
                        val jsonString = byteArrayOutputStream.toByteArray().toString(Charsets.UTF_8)
                        json = JSONObject(jsonString)
                        metadata = loadMetadata(json)
                        metadata[CwfMetadataKey.CWF_FILENAME] = zipName.substringBeforeLast(".")
                        metadata[CwfMetadataKey.CWF_AUTHORIZATION] = authorization.toString()
                    } else {
                        val cwfResFileMap = ResFileMap.fromFileName(entryName)
                        val drawableFormat = ResFormat.fromFileName(entryName)
                        if (cwfResFileMap != ResFileMap.UNKNOWN && drawableFormat != ResFormat.UNKNOWN) {
                            resData[cwfResFileMap.fileName] = ResData(byteArrayOutputStream.toByteArray(), drawableFormat)
                        } else if (drawableFormat != ResFormat.UNKNOWN)
                            resData[entryName.substringBeforeLast(".")] = ResData(byteArrayOutputStream.toByteArray(), drawableFormat)
                    }
                    zipEntry = zipInputStream.nextEntry
                }

                // Valid CWF file must contains a valid json file with a name within metadata and a custom watchface image
                return if (metadata.containsKey(CwfMetadataKey.CWF_NAME) && resData.containsKey(ResFileMap.CUSTOM_WATCHFACE.fileName))
                    CwfFile(CwfData(json.toString(4), metadata, resData), byteArray)
                else
                    null

            } catch (_: Exception) {
                return null     // mainly IOException
            }
        }

        fun saveCustomWatchface(contentResolver: ContentResolver, file: DocumentFile, customWatchface: CwfData) {

            try {
                val outputStream = FileOutputStream(contentResolver.openFileDescriptor(file.uri, "w")?.fileDescriptor)
                val zipOutputStream = ZipOutputStream(BufferedOutputStream(outputStream))

                val jsonEntry = ZipEntry(CWF_JSON_FILE)
                zipOutputStream.putNextEntry(jsonEntry)
                zipOutputStream.write(customWatchface.json.toByteArray())
                zipOutputStream.closeEntry()

                for (resData in customWatchface.resData) {
                    val fileEntry = ZipEntry("${resData.key}.${resData.value.format.extension}")
                    zipOutputStream.putNextEntry(fileEntry)
                    zipOutputStream.write(resData.value.value)
                    zipOutputStream.closeEntry()
                }
                zipOutputStream.close()
                outputStream.close()
            } catch (_: Exception) {
                // Ignore file
            }

        }

        fun loadMetadata(contents: JSONObject): CwfMetadataMap {
            val metadata: CwfMetadataMap = mutableMapOf()

            contents.optJSONObject(JsonKeys.METADATA.key)?.also { jsonObject ->                     // optJSONObject doesn't throw Exception
                for (key in jsonObject.keys()) {
                    CwfMetadataKey.fromKey(key)?.let { metadata[it] = jsonObject.optString(key) }   // optString doesn't throw Exception
                }
            }
            return metadata
        }

        private fun byteArrayToZipInputStream(byteArray: ByteArray): ZipInputStream {
            val byteArrayInputStream = ByteArrayInputStream(byteArray)
            return ZipInputStream(byteArrayInputStream)
        }
    }
}