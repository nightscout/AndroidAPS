package info.nightscout.configuration.maintenance.formats

import info.nightscout.core.utils.CryptoUtil
import info.nightscout.interfaces.storage.Storage
import info.nightscout.rx.weardata.CustomWatchface
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataKey
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataMap
import info.nightscout.rx.weardata.CustomWatchfaceFormat
import info.nightscout.rx.weardata.CustomWatchfaceMetadataKey
import info.nightscout.rx.weardata.CustomWatchfaceMetadataMap
import info.nightscout.rx.weardata.DrawableData
import info.nightscout.rx.weardata.DrawableFormat
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZipCustomWatchfaceFormat @Inject constructor(
    private var rh: ResourceHelper,
    private var cryptoUtil: CryptoUtil,
    private var storage: Storage
) : CustomWatchfaceFormat {

    companion object {

        const val CUSTOM_WF_EXTENTION = ".zip"
        const val CUSTOM_JSON_FILE = "CustomWatchface.json"
    }

    override fun loadCustomWatchface(cwfFile: File): CustomWatchface? {
        var json = JSONObject()
        var metadata: CustomWatchfaceMetadataMap = mutableMapOf()
        val drawableDatas: CustomWatchfaceDrawableDataMap = mutableMapOf()

        try {
            val zipInputStream  = ZipInputStream(cwfFile.inputStream())
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

                if (entryName == CUSTOM_JSON_FILE) {
                    val jsonString = byteArrayOutputStream.toByteArray().toString(Charsets.UTF_8)
                    json = JSONObject(jsonString)
                    metadata = loadMetadata(json)
                } else {
                    val customWatchfaceDrawableDataKey = CustomWatchfaceDrawableDataKey.fromFileName(entryName)
                    val drawableFormat = DrawableFormat.fromFileName(entryName)
                    if (customWatchfaceDrawableDataKey != CustomWatchfaceDrawableDataKey.UNKNOWN && drawableFormat != DrawableFormat.UNKNOWN) {
                        drawableDatas[customWatchfaceDrawableDataKey] = DrawableData(byteArrayOutputStream.toByteArray(),drawableFormat)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }

            // Valid CWF file must contains a valid json file with a name within metadata and a custom watchface image
            if (metadata.containsKey(CustomWatchfaceMetadataKey.CWF_NAME) && drawableDatas.containsKey(CustomWatchfaceDrawableDataKey.CUSTOM_WATCHFACE))
                return CustomWatchface(json.toString(4), metadata, drawableDatas)
            else
                return null

        } catch (e: Exception) {
            return null
        }
    }


    override fun saveCustomWatchface(file: File, customWatchface: EventData.ActionSetCustomWatchface) {

        try {
            val outputStream = FileOutputStream(file)
            val zipOutputStream = ZipOutputStream(BufferedOutputStream(outputStream))

            // Ajouter le fichier JSON au ZIP
            val jsonEntry = ZipEntry(CUSTOM_JSON_FILE)
            zipOutputStream.putNextEntry(jsonEntry)
            zipOutputStream.write(customWatchface.json.toByteArray())
            zipOutputStream.closeEntry()

            // Ajouter les fichiers divers au ZIP
            for (drawableData in customWatchface.drawableDataMap) {
                val fileEntry = ZipEntry("${drawableData.key.fileName}.${drawableData.value.format.extension}")
                zipOutputStream.putNextEntry(fileEntry)
                zipOutputStream.write(drawableData.value.value)
                zipOutputStream.closeEntry()
            }
            zipOutputStream.close()
            outputStream.close()
        } catch (e: Exception) {

        }

    }


    override fun loadMetadata(contents: JSONObject): CustomWatchfaceMetadataMap {
        val metadata: CustomWatchfaceMetadataMap = mutableMapOf()

        if (contents.has("metadata")) {
            val meta = contents.getJSONObject("metadata")
            for (key in meta.keys()) {
                val metaKey = CustomWatchfaceMetadataKey.fromKey(key)
                if (metaKey != null) {
                    metadata[metaKey] = meta.getString(key)
                }
            }
        }
        return metadata
    }

}