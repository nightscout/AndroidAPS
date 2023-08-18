package info.nightscout.rx.weardata

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import info.nightscout.shared.R
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val CUSTOM_VERSION = "0.8"
enum class CustomWatchfaceDrawableDataKey(val key: String, @DrawableRes val icon: Int?, val fileName: String) {
    UNKNOWN("unknown", null, "Unknown"),
    CUSTOM_WATCHFACE("customWatchface", R.drawable.watchface_custom, "CustomWatchface"),
    BACKGROUND("background", R.drawable.background, "Background"),
    BACKGROUND_HIGH("background", R.drawable.background, "BackgroundHigh"),
    BACKGROUND_LOW("background", R.drawable.background, "BackgroundLow"),
    COVERCHART("cover_chart", null, "CoverChart"),
    COVERPLATE("cover_plate", R.drawable.simplified_dial, "CoverPlate"),
    HOURHAND("hour_hand", R.drawable.hour_hand, "HourHand"),
    MINUTEHAND("minute_hand", R.drawable.minute_hand, "MinuteHand"),
    SECONDHAND("second_hand", R.drawable.second_hand, "SecondHand");

    companion object {
        fun fromKey(key: String): CustomWatchfaceDrawableDataKey =
            values().firstOrNull { it.key == key } ?: UNKNOWN
        fun fromFileName(file: String): CustomWatchfaceDrawableDataKey = values().firstOrNull { it.fileName == file.substringBeforeLast(".") } ?: UNKNOWN
    }
}

enum class DrawableFormat(val extension: String) {
    UNKNOWN(""),
    //XML("xml"),
    //SVG("svg"),
    JPG("jpg"),
    PNG("png");

    companion object {
        fun fromFileName(fileName: String): DrawableFormat =
            values().firstOrNull { it.extension == fileName.substringAfterLast(".") } ?: UNKNOWN

    }
}

@Serializable
data class DrawableData(val value: ByteArray, val format: DrawableFormat) {

    fun toDrawable(resources: Resources): Drawable? {
        try {
            return when (format) {
                DrawableFormat.PNG, DrawableFormat.JPG -> {
                    val bitmap = BitmapFactory.decodeByteArray(value, 0, value.size)
                    BitmapDrawable(resources, bitmap)
                }
                /*
                                DrawableFormat.SVG -> {
                                    //TODO: include svg to Drawable convertor here
                                    null
                                }
                                DrawableFormat.XML -> {
                                    // Always return a null Drawable, even if xml file is a valid xml vector file
                                    val xmlInputStream = ByteArrayInputStream(value)
                                    val xmlPullParser = Xml.newPullParser()
                                    xmlPullParser.setInput(xmlInputStream, null)
                                    Drawable.createFromXml(resources, xmlPullParser)
                                }
                */
                else                                   -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
}

typealias CustomWatchfaceDrawableDataMap = MutableMap<CustomWatchfaceDrawableDataKey, DrawableData>
typealias CustomWatchfaceMetadataMap = MutableMap<CustomWatchfaceMetadataKey, String>

@Serializable
data class CustomWatchfaceData(val json: String, var metadata: CustomWatchfaceMetadataMap, val drawableDatas: CustomWatchfaceDrawableDataMap)

enum class CustomWatchfaceMetadataKey(val key: String, @StringRes val label: Int) {

    CWF_NAME("name", R.string.metadata_label_watchface_name),
    CWF_FILENAME("filename", R.string.metadata_wear_import_filename),
    CWF_AUTHOR("author", R.string.metadata_label_watchface_author),
    CWF_CREATED_AT("created_at", R.string.metadata_label_watchface_created_at),
    CWF_VERSION("cwf_version", R.string.metadata_label_plugin_version),
    CWF_AUTHOR_VERSION("author_version", R.string.metadata_label_watchface_name_version),
    CWF_COMMENT("comment", R.string.metadata_label_watchface_comment), // label not planed to be used for CWF_COMMENT
    CWF_AUTHORIZATION("cwf_authorization", R.string.metadata_label_watchface_authorization),
    CWF_PREF_AAPS_DETAILED_IOB("key_wear_detailediob", R.string.metadata_label_watchface_pref),
    CWF_PREF_AAPS_DETAILED_DELTA("key_wear_detailed_delta", R.string.metadata_label_watchface_pref),
    CWF_PREF_AAPS_BGI("key_wear_showbgi", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_IOB("key_show_iob", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_COB("key_show_cob", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_DELTA("key_show_delta", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_AVG_DELTA("key_show_avg_delta", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY("key_show_uploader_battery", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_RIG_BATTERY("key_show_rig_battery", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_TEMP_BASAL("key_show_temp_basal", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_DIRECTION("key_show_direction", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_AGO("key_show_ago", R.string.metadata_label_watchface_pref),
    CWF_PREF_WATCH_SHOW_BG("key_show_bg", R.string.metadata_label_watchface_pref);
    companion object {
        fun fromKey(key: String): CustomWatchfaceMetadataKey? =
            values().firstOrNull { it.key == key }
    }
}

class ZipWatchfaceFormat {
    companion object {

        const val CUSTOM_WF_EXTENTION = ".zip"
        const val CUSTOM_JSON_FILE = "CustomWatchface.json"

        fun loadCustomWatchface(cwfFile: File, authorization: Boolean): CustomWatchfaceData? {
            var json = JSONObject()
            var metadata: CustomWatchfaceMetadataMap = mutableMapOf()
            val drawableDatas: CustomWatchfaceDrawableDataMap = mutableMapOf()

            try {
                val zipInputStream = ZipInputStream(cwfFile.inputStream())
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
                        metadata[CustomWatchfaceMetadataKey.CWF_FILENAME] = cwfFile.name
                        metadata[CustomWatchfaceMetadataKey.CWF_AUTHORIZATION] = authorization.toString()
                    } else {
                        val customWatchfaceDrawableDataKey = CustomWatchfaceDrawableDataKey.fromFileName(entryName)
                        val drawableFormat = DrawableFormat.fromFileName(entryName)
                        if (customWatchfaceDrawableDataKey != CustomWatchfaceDrawableDataKey.UNKNOWN && drawableFormat != DrawableFormat.UNKNOWN) {
                            drawableDatas[customWatchfaceDrawableDataKey] = DrawableData(byteArrayOutputStream.toByteArray(), drawableFormat)
                        }
                    }
                    zipEntry = zipInputStream.nextEntry
                }

                // Valid CWF file must contains a valid json file with a name within metadata and a custom watchface image
                if (metadata.containsKey(CustomWatchfaceMetadataKey.CWF_NAME) && drawableDatas.containsKey(CustomWatchfaceDrawableDataKey.CUSTOM_WATCHFACE))
                    return CustomWatchfaceData(json.toString(4), metadata, drawableDatas)
                else
                    return null

            } catch (e: Exception) {
                return null
            }
        }

        fun saveCustomWatchface(file: File, customWatchface: CustomWatchfaceData) {

            try {
                val outputStream = FileOutputStream(file)
                val zipOutputStream = ZipOutputStream(BufferedOutputStream(outputStream))

                // Ajouter le fichier JSON au ZIP
                val jsonEntry = ZipEntry(CUSTOM_JSON_FILE)
                zipOutputStream.putNextEntry(jsonEntry)
                zipOutputStream.write(customWatchface.json.toByteArray())
                zipOutputStream.closeEntry()

                // Ajouter les fichiers divers au ZIP
                for (drawableData in customWatchface.drawableDatas) {
                    val fileEntry = ZipEntry("${drawableData.key.fileName}.${drawableData.value.format.extension}")
                    zipOutputStream.putNextEntry(fileEntry)
                    zipOutputStream.write(drawableData.value.value)
                    zipOutputStream.closeEntry()
                }
                zipOutputStream.close()
                outputStream.close()
            } catch (_: Exception) {
            }

        }

        fun loadMetadata(contents: JSONObject): CustomWatchfaceMetadataMap {
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

}