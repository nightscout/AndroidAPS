package info.nightscout.rx.weardata

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.Xml
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import info.nightscout.shared.R
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File

enum class CustomWatchfaceDrawableDataKey(val key: String, @DrawableRes val icon: Int?, val fileName: String) {
    UNKNOWN("unknown", null,"Unknown"),
    CUSTOM_WATCHFACE("customWatchface", R.drawable.watchface_custom, "CustomWatchface"),
    BACKGROUND("background", R.drawable.background, "Background"),
    COVERCHART("cover_chart", null,"CoverChart"),
    COVERPLATE("cover_plate", R.drawable.simplified_dial, "CoverPlate"),
    HOURHAND("hour_hand", R.drawable.hour_hand,"HourHand"),
    MINUTEHAND("minute_hand", R.drawable.minute_hand,"MinuteHand"),
    SECONDHAND("second_hand", R.drawable.second_hand, "SecondHand");

    companion object {

        private val keyToEnumMap = HashMap<String, CustomWatchfaceDrawableDataKey>()
        private val fileNameToEnumMap = HashMap<String, CustomWatchfaceDrawableDataKey>()

        init {
            for (value in values()) keyToEnumMap[value.key] = value
            for (value in values()) fileNameToEnumMap[value.fileName] = value
        }

        fun fromKey(key: String): CustomWatchfaceDrawableDataKey =
            if (keyToEnumMap.containsKey(key)) {
                keyToEnumMap[key] ?:UNKNOWN
            } else {
                UNKNOWN
            }

        fun fromFileName(file: String): CustomWatchfaceDrawableDataKey =
            if (fileNameToEnumMap.containsKey(file.substringBeforeLast("."))) {
                fileNameToEnumMap[file.substringBeforeLast(".")] ?:UNKNOWN
            } else {
                UNKNOWN
            }
    }

}

enum class DrawableFormat(val extension: String) {
    UNKNOWN(""),
    //XML("xml"),
    //svg("svg"),
    JPG("jpg"),
    PNG("png");

    companion object {

        private val extensionToEnumMap = HashMap<String, DrawableFormat>()

        init {
            for (value in values()) extensionToEnumMap[value.extension] = value
        }

        fun fromFileName(fileName: String): DrawableFormat =
            if (extensionToEnumMap.containsKey(fileName.substringAfterLast("."))) {
                extensionToEnumMap[fileName.substringAfterLast(".")] ?:UNKNOWN
            } else {
                UNKNOWN
            }
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
                DrawableFormat.XML -> {
                    val xmlInputStream = ByteArrayInputStream(value)
                    val xmlPullParser = Xml.newPullParser()
                    xmlPullParser.setInput(xmlInputStream, null)
                    Drawable.createFromXml(resources, xmlPullParser)
                }
*/
                else               -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
}

typealias CustomWatchfaceDrawableDataMap = MutableMap<CustomWatchfaceDrawableDataKey, DrawableData>
typealias CustomWatchfaceMetadataMap = MutableMap<CustomWatchfaceMetadataKey, String>

data class CustomWatchface(val json: String, var metadata: CustomWatchfaceMetadataMap, val drawableDatas: CustomWatchfaceDrawableDataMap)

interface CustomWatchfaceFormat {

    fun saveCustomWatchface(file: File, customWatchface: EventData.ActionSetCustomWatchface)
    fun loadCustomWatchface(cwfFile: File): CustomWatchface?
    fun loadMetadata(contents: JSONObject): CustomWatchfaceMetadataMap
}

enum class CustomWatchfaceMetadataKey(val key: String, @StringRes val label: Int) {

    CWF_NAME("name", R.string.metadata_label_watchface_name),
    CWF_AUTHOR("author", R.string.metadata_label_watchface_author),
    CWF_CREATED_AT("created_at", R.string.metadata_label_watchface_created_at),
    CWF_VERSION("cwf_version", R.string.metadata_label_watchface_version);

    companion object {

        private val keyToEnumMap = HashMap<String, CustomWatchfaceMetadataKey>()

        init {
            for (value in values()) keyToEnumMap[value.key] = value
        }

        fun fromKey(key: String): CustomWatchfaceMetadataKey? =
            if (keyToEnumMap.containsKey(key)) {
                keyToEnumMap[key]
            } else {
                null
            }

    }

}