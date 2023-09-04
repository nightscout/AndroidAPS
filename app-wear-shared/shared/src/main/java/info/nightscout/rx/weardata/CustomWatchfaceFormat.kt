package info.nightscout.rx.weardata

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.annotation.StringRes
import com.caverock.androidsvg.SVG
import info.nightscout.shared.R
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val CUSTOM_VERSION = "1.0"

enum class ResFileMap(val fileName: String) {
    UNKNOWN("Unknown"),
    CUSTOM_WATCHFACE("CustomWatchface"),
    BACKGROUND("Background"),
    BACKGROUND_HIGH("BackgroundHigh"),
    BACKGROUND_LOW("BackgroundLow"),
    COVER_CHART("CoverChart"),
    COVER_CHART_HIGH("CoverChartHigh"),
    COVER_CHART_LOW("CoverChartLow"),
    COVER_PLATE("CoverPlate"),
    COVER_PLATE_HIGH("CoverPlateHigh"),
    COVER_PLATE_LOW("CoverPlateLow"),
    HOUR_HAND("HourHand"),
    HOUR_HAND_HIGH("HourHandHigh"),
    HOUR_HAND_LOW("HourHandLow"),
    MINUTE_HAND("MinuteHand"),
    MINUTE_HAND_HIGH("MinuteHandHigh"),
    MINUTE_HAND_LOW("MinuteHandLow"),
    SECOND_HAND("SecondHand"),
    SECOND_HAND_HIGH("SecondHandHigh"),
    SECOND_HAND_LOW("SecondHandLow"),
    ARROW_NONE("ArrowNone"),
    ARROW_DOUBLE_UP("ArrowDoubleUp"),
    ARROW_SINGLE_UP("ArrowSingleUp"),
    ARROW_FORTY_FIVE_UP("Arrow45Up"),
    ARROW_FLAT("ArrowFlat"),
    ARROW_FORTY_FIVE_DOWN("Arrow45Down"),
    ARROW_SINGLE_DOWN("ArrowSingleDown"),
    ARROW_DOUBLE_DOWN("ArrowDoubleDown"),
    FONT1("Font1"),
    FONT2("Font2"),
    FONT3("Font3"),
    FONT4("Font4");

    companion object {

        fun fromFileName(file: String): ResFileMap = values().firstOrNull { it.fileName == file.substringBeforeLast(".") } ?: UNKNOWN
    }
}

enum class ResFormat(val extension: String) {
    UNKNOWN(""),
    SVG("svg"),
    JPG("jpg"),
    PNG("png"),
    TTF("ttf");

    companion object {

        fun fromFileName(fileName: String): ResFormat =
            values().firstOrNull { it.extension == fileName.substringAfterLast(".").lowercase() } ?: UNKNOWN

    }
}

@Serializable
data class ResData(val value: ByteArray, val format: ResFormat) {

    fun toDrawable(resources: Resources): Drawable? {
        try {
            return when (format) {
                ResFormat.PNG, ResFormat.JPG -> {
                    val bitmap = BitmapFactory.decodeByteArray(value, 0, value.size)
                    BitmapDrawable(resources, bitmap)
                }

                ResFormat.SVG                -> {
                    val svg = SVG.getFromInputStream(ByteArrayInputStream(value))
                    val picture = svg.renderToPicture()
                    PictureDrawable(picture).apply {
                        setBounds(0, 0, svg.documentWidth.toInt(), svg.documentHeight.toInt())
                    }
                }

                else                         -> null
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun toTypeface(): Typeface? {
        try {
            return when (format) {
                ResFormat.TTF -> {
                    // Workaround with temporary File, Typeface.createFromFileDescriptor(null, value, 0, value.size) more simple not available
                    File.createTempFile("temp", ".ttf").let { tempFile ->
                        FileOutputStream(tempFile).let { fileOutputStream ->
                            fileOutputStream.write(value)
                            fileOutputStream.close()
                        }

                        Typeface.createFromFile(tempFile).let {
                            if (!tempFile.delete()) {
                                // delete tempfile after usage
                            }
                            it
                        }
                    }
                }

                else          -> {
                    null

                }
            }
        } catch (e: Exception) {
            return null
        }
    }
}

typealias CwfResDataMap = MutableMap<ResFileMap, ResData>
typealias CwfMetadataMap = MutableMap<CwfMetadataKey, String>

@Serializable
data class CwfData(val json: String, var metadata: CwfMetadataMap, val resDatas: CwfResDataMap)

enum class CwfMetadataKey(val key: String, @StringRes val label: Int, val isPref: Boolean) {

    CWF_NAME("name", R.string.metadata_label_watchface_name, false),
    CWF_FILENAME("filename", R.string.metadata_wear_import_filename, false),
    CWF_AUTHOR("author", R.string.metadata_label_watchface_author, false),
    CWF_CREATED_AT("created_at", R.string.metadata_label_watchface_created_at, false),
    CWF_VERSION("cwf_version", R.string.metadata_label_plugin_version, false),
    CWF_AUTHOR_VERSION("author_version", R.string.metadata_label_watchface_name_version, false),
    CWF_COMMENT("comment", R.string.metadata_label_watchface_infos, false),
    CWF_AUTHORIZATION("cwf_authorization", R.string.metadata_label_watchface_authorization, false),
    CWF_PREF_WATCH_SHOW_DETAILED_IOB("key_show_detailed_iob", R.string.pref_show_detailed_iob, true),
    CWF_PREF_WATCH_SHOW_DETAILED_DELTA("key_show_detailed_delta", R.string.pref_show_detailed_delta, true),
    CWF_PREF_WATCH_SHOW_BGI("key_show_bgi", R.string.pref_show_bgi, true),
    CWF_PREF_WATCH_SHOW_IOB("key_show_iob", R.string.pref_show_iob, true),
    CWF_PREF_WATCH_SHOW_COB("key_show_cob", R.string.pref_show_cob, true),
    CWF_PREF_WATCH_SHOW_DELTA("key_show_delta", R.string.pref_show_delta, true),
    CWF_PREF_WATCH_SHOW_AVG_DELTA("key_show_avg_delta", R.string.pref_show_avgdelta, true),
    CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY("key_show_uploader_battery", R.string.pref_show_phone_battery, true),
    CWF_PREF_WATCH_SHOW_RIG_BATTERY("key_show_rig_battery", R.string.pref_show_rig_battery, true),
    CWF_PREF_WATCH_SHOW_TEMP_BASAL("key_show_temp_basal", R.string.pref_show_basal_rate, true),
    CWF_PREF_WATCH_SHOW_DIRECTION("key_show_direction", R.string.pref_show_direction_arrow, true),
    CWF_PREF_WATCH_SHOW_AGO("key_show_ago", R.string.pref_show_ago, true),
    CWF_PREF_WATCH_SHOW_BG("key_show_bg", R.string.pref_show_bg, true),
    CWF_PREF_WATCH_SHOW_LOOP_STATUS("key_show_loop_status", R.string.pref_show_loop_status, true);

    companion object {

        fun fromKey(key: String): CwfMetadataKey? =
            values().firstOrNull { it.key == key }
    }
}

enum class ViewKeys(val key: String, @StringRes val comment: Int) {

    BACKGROUND("background", R.string.cwf_comment_background),
    CHART("chart", R.string.cwf_comment_chart),
    COVER_CHART("cover_chart", R.string.cwf_comment_cover_chart),
    FREETEXT1("freetext1", R.string.cwf_comment_freetext1),
    FREETEXT2("freetext2", R.string.cwf_comment_freetext2),
    FREETEXT3("freetext3", R.string.cwf_comment_freetext3),
    FREETEXT4("freetext4", R.string.cwf_comment_freetext4),
    IOB1("iob1", R.string.cwf_comment_iob1),
    IOB2("iob2", R.string.cwf_comment_iob2),
    COB1("cob1", R.string.cwf_comment_cob1),
    COB2("cob2", R.string.cwf_comment_cob2),
    DELTA("delta", R.string.cwf_comment_delta),
    AVG_DELTA("avg_delta", R.string.cwf_comment_avg_delta),
    UPLOADER_BATTERY("uploader_battery", R.string.cwf_comment_uploader_battery),
    RIG_BATTERY("rig_battery", R.string.cwf_comment_rig_battery),
    BASALRATE("basalRate", R.string.cwf_comment_basalRate),
    BGI("bgi", R.string.cwf_comment_bgi),
    TIME("time", R.string.cwf_comment_time),
    HOUR("hour", R.string.cwf_comment_hour),
    MINUTE("minute", R.string.cwf_comment_minute),
    SECOND("second", R.string.cwf_comment_second),
    TIMEPERIOD("timePeriod", R.string.cwf_comment_timePeriod),
    DAY_NAME("day_name", R.string.cwf_comment_day_name),
    DAY("day", R.string.cwf_comment_day),
    MONTH("month", R.string.cwf_comment_month),
    LOOP("loop", R.string.cwf_comment_loop),
    DIRECTION("direction", R.string.cwf_comment_direction),
    TIMESTAMP("timestamp", R.string.cwf_comment_timestamp),
    SGV("sgv", R.string.cwf_comment_sgv),
    COVER_PLATE("cover_plate", R.string.cwf_comment_cover_plate),
    HOUR_HAND("hour_hand", R.string.cwf_comment_hour_hand),
    MINUTE_HAND("minute_hand", R.string.cwf_comment_minute_hand),
    SECOND_HAND("second_hand", R.string.cwf_comment_second_hand);
}

enum class JsonKeys(val key: String, val viewType: ViewType, @StringRes val comment: Int?) {
    METADATA("metadata", ViewType.NONE, null),
    ENABLESECOND("enableSecond", ViewType.NONE, null),
    HIGHCOLOR("highColor", ViewType.NONE, null),
    MIDCOLOR("midColor", ViewType.NONE, null),
    LOWCOLOR("lowColor", ViewType.NONE, null),
    LOWBATCOLOR("lowBatColor", ViewType.NONE, null),
    CARBCOLOR("carbColor", ViewType.NONE, null),
    BASALBACKGROUNDCOLOR("basalBackgroundColor", ViewType.NONE, null),
    BASALCENTERCOLOR("basalCenterColor", ViewType.NONE, null),
    GRIDCOLOR("gridColor", ViewType.NONE, null),
    POINTSIZE("pointSize", ViewType.NONE, null),
    WIDTH("width", ViewType.ALLVIEWS, null),
    HEIGHT("height", ViewType.ALLVIEWS, null),
    TOPMARGIN("topmargin", ViewType.ALLVIEWS, null),
    LEFTMARGIN("leftmargin", ViewType.ALLVIEWS, null),
    ROTATION("rotation", ViewType.TEXTVIEW, null),
    VISIBILITY("visibility", ViewType.ALLVIEWS, null),
    TEXTSIZE("textsize", ViewType.TEXTVIEW, null),
    TEXTVALUE("textvalue", ViewType.TEXTVIEW, null),
    GRAVITY("gravity", ViewType.TEXTVIEW, null),
    FONT("font", ViewType.TEXTVIEW, null),
    FONTSTYLE("fontStyle", ViewType.TEXTVIEW, null),
    FONTCOLOR("fontColor", ViewType.TEXTVIEW, null),
    COLOR("color", ViewType.IMAGEVIEW, null),
    ALLCAPS("allCaps", ViewType.TEXTVIEW, null),
    DAYNAMEFORMAT("dayNameFormat", ViewType.NONE, null),
    MONTHFORMAT("monthFormat", ViewType.NONE, null)
}

enum class JsonKeyValues(val key: String, val jsonKey: JsonKeys) {
    GONE("gone", JsonKeys.VISIBILITY),
    VISIBLE("visible", JsonKeys.VISIBILITY),
    INVISIBLE("invisible", JsonKeys.VISIBILITY),
    CENTER("center", JsonKeys.GRAVITY),
    LEFT("left", JsonKeys.GRAVITY),
    RIGHT("right", JsonKeys.GRAVITY),
    SANS_SERIF("sans_serif", JsonKeys.FONT),
    DEFAULT("default", JsonKeys.FONT),
    DEFAULT_BOLD("default_bold", JsonKeys.FONT),
    MONOSPACE("monospace", JsonKeys.FONT),
    SERIF("serif", JsonKeys.FONT),
    ROBOTO_CONDENSED_BOLD("roboto_condensed_bold", JsonKeys.FONT),
    ROBOTO_CONDENSED_LIGHT("roboto_condensed_light", JsonKeys.FONT),
    ROBOTO_CONDENSED_REGULAR("roboto_condensed_regular", JsonKeys.FONT),
    ROBOTO_SLAB_LIGHT("roboto_slab_light", JsonKeys.FONT),
    NORMAL("normal", JsonKeys.FONTSTYLE),
    BOLD("bold", JsonKeys.FONTSTYLE),
    BOLD_ITALIC("bold_italic", JsonKeys.FONTSTYLE),
    ITALIC("italic", JsonKeys.FONTSTYLE),
    BGCOLOR("bgColor", JsonKeys.COLOR),
    FONT1("font1", JsonKeys.FONTCOLOR),
    FONT2("font2", JsonKeys.FONTCOLOR),
    FONT3("font3", JsonKeys.FONTCOLOR),
    FONT4("font4", JsonKeys.FONTCOLOR)
}

enum class ViewType(@StringRes val comment: Int?) {
    NONE(null),
    TEXTVIEW(null),
    IMAGEVIEW(null),
    ALLVIEWS(null)
}

class ZipWatchfaceFormat {
    companion object {

        const val CWF_EXTENTION = ".zip"
        const val CWF_JSON_FILE = "CustomWatchface.json"

        fun loadCustomWatchface(cwfFile: File, authorization: Boolean): CwfData? {
            var json = JSONObject()
            var metadata: CwfMetadataMap = mutableMapOf()
            val resDatas: CwfResDataMap = mutableMapOf()

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

                    if (entryName == CWF_JSON_FILE) {
                        val jsonString = byteArrayOutputStream.toByteArray().toString(Charsets.UTF_8)
                        json = JSONObject(jsonString)
                        metadata = loadMetadata(json)
                        metadata[CwfMetadataKey.CWF_FILENAME] = cwfFile.name
                        metadata[CwfMetadataKey.CWF_AUTHORIZATION] = authorization.toString()
                    } else {
                        val cwfResFileMap = ResFileMap.fromFileName(entryName)
                        val drawableFormat = ResFormat.fromFileName(entryName)
                        if (cwfResFileMap != ResFileMap.UNKNOWN && drawableFormat != ResFormat.UNKNOWN) {
                            resDatas[cwfResFileMap] = ResData(byteArrayOutputStream.toByteArray(), drawableFormat)
                        }
                    }
                    zipEntry = zipInputStream.nextEntry
                }

                // Valid CWF file must contains a valid json file with a name within metadata and a custom watchface image
                return if (metadata.containsKey(CwfMetadataKey.CWF_NAME) && resDatas.containsKey(ResFileMap.CUSTOM_WATCHFACE))
                    CwfData(json.toString(4), metadata, resDatas)
                else
                    null

            } catch (e: Exception) {
                return null     // mainly IOException
            }
        }

        fun saveCustomWatchface(file: File, customWatchface: CwfData) {

            try {
                val outputStream = FileOutputStream(file)
                val zipOutputStream = ZipOutputStream(BufferedOutputStream(outputStream))
                
                val jsonEntry = ZipEntry(CWF_JSON_FILE)
                zipOutputStream.putNextEntry(jsonEntry)
                zipOutputStream.write(customWatchface.json.toByteArray())
                zipOutputStream.closeEntry()

                for (resData in customWatchface.resDatas) {
                    val fileEntry = ZipEntry("${resData.key.fileName}.${resData.value.format.extension}")
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
    }

}