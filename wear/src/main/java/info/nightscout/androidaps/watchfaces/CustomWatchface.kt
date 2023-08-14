@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.app.ActionBar.LayoutParams
import android.content.Context
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.graphics.Typeface
import android.support.wearable.watchface.WatchFaceStyle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.forEach
import androidx.viewbinding.ViewBinding
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityCustomBinding
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace
import info.nightscout.rx.weardata.CUSTOM_VERSION
import info.nightscout.rx.weardata.CustomWatchfaceData
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataKey
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataMap
import info.nightscout.rx.weardata.CustomWatchfaceMetadataKey
import info.nightscout.rx.weardata.DrawableData
import info.nightscout.rx.weardata.DrawableFormat
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.ZipWatchfaceFormat
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.extensions.toVisibilityKeepSpace
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.TimeOfDay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class CustomWatchface : BaseWatchFace() {

    @Inject lateinit var context: Context
    private lateinit var binding: ActivityCustomBinding
    private var zoomFactor = 1.0
    private val displaySize = Point()
    private val TEMPLATE_RESOLUTION = 400
    private var lowBatColor = Color.RED
    private var bgColor = Color.WHITE


    @Suppress("DEPRECATION")
    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityCustomBinding.inflate(inflater)
        setDefaultColors()
        persistence.store(defaultWatchface(), true)
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(displaySize)
        zoomFactor = (displaySize.x).toDouble() / TEMPLATE_RESOLUTION.toDouble()
        return binding
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this)
            .setAcceptsTapEvents(true)
            .setHideNotificationIndicator(false)
            .setShowUnreadCountIndicator(true)
            .build()
    }

    override fun setDataFields() {
        super.setDataFields()
        binding.direction2.setImageDrawable(resources.getDrawable(TrendArrow.fromSymbol(singleBg.slopeArrow).icon))
    }
    override fun setColorDark() {
        setWatchfaceStyle()
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.sgv.setTextColor(bgColor)
        binding.direction.setTextColor(bgColor)
        binding.direction2.colorFilter = changeDrawableColor(bgColor)

        if (ageLevel != 1)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if (status.batteryLevel != 1)
            binding.uploaderBattery.setTextColor(lowBatColor)
        when (loopLevel) {
            -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
            else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
        }

        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)

        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
        // rotate the minute hand.
        binding.minuteHand.rotation = TimeOfDay().minuteOfHour * 6f
        // rotate the hour hand.
        binding.hourHand.rotation = TimeOfDay().hourOfDay * 30f + TimeOfDay().minuteOfHour * 0.5f

        setupCharts()
    }

    override fun setColorBright() {
        setColorDark()
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setSecond() {
        binding.time.text = "${dateUtil.hourString()}:${dateUtil.minuteString()}" + if (showSecond) ":${dateUtil.secondString()}" else ""
        binding.second.text = dateUtil.secondString()
        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
        //aapsLogger.debug("XXXXX SetSecond $watchModeString")
    }

    override fun updateSecondVisibility() {
        binding.second.visibility = showSecond.toVisibility()
        binding.secondHand.visibility = showSecond.toVisibility()
    }

    private fun setWatchfaceStyle() {
        val customWatchface = persistence.readCustomWatchface() ?: persistence.readCustomWatchface(true)
        customWatchface?.let {
            try {
                val json = JSONObject(it.customWatchfaceData.json)
                val drawableDataMap = it.customWatchfaceData.drawableDatas
                enableSecond = (if (json.has("enableSecond")) json.getBoolean("enableSecond") else false) && sp.getBoolean(R.string.key_show_seconds, true)

                highColor = if (json.has("highColor")) Color.parseColor(json.getString("highColor")) else ContextCompat.getColor(this, R.color.dark_highColor)
                midColor = if (json.has("midColor")) Color.parseColor(json.getString("midColor")) else ContextCompat.getColor(this, R.color.inrange)
                lowColor = if (json.has("lowColor")) Color.parseColor(json.getString("lowColor")) else ContextCompat.getColor(this, R.color.low)
                lowBatColor = if (json.has("lowBatColor")) Color.parseColor(json.getString("lowBatColor")) else ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
                carbColor = if (json.has("carbColor")) Color.parseColor(json.getString("carbColor")) else ContextCompat.getColor(this, R.color.carbs)
                gridColor = if (json.has("gridColor")) Color.parseColor(json.getString("gridColor")) else ContextCompat.getColor(this, R.color.carbs)
                pointSize = if (json.has("pointSize")) json.getInt("pointSize") else 2
                bgColor = when (singleBg.sgvLevel) {
                    1L   -> highColor
                    0L   -> midColor
                    -1L  -> lowColor
                    else -> midColor
                }

                binding.mainLayout.forEach { view ->
                    CustomViews.fromId(view.id)?.let { id ->
                        if (json.has(id.key)) {
                            var viewjson = json.getJSONObject(id.key)
                            var wrapContent = LayoutParams.WRAP_CONTENT
                            val width = if (viewjson.has("width")) (viewjson.getInt("width") * zoomFactor).toInt() else wrapContent
                            val height = if (viewjson.has("height")) (viewjson.getInt("height") * zoomFactor).toInt() else wrapContent
                            var params = FrameLayout.LayoutParams(width, height)
                            params.topMargin = if (viewjson.has("topmargin")) (viewjson.getInt("topmargin") * zoomFactor).toInt() else 0
                            params.leftMargin = if (viewjson.has("leftmargin")) (viewjson.getInt("leftmargin") * zoomFactor).toInt() else 0
                            view.setLayoutParams(params)
                            view.visibility = if (viewjson.has("visibility")) setVisibility(viewjson.getString("visibility"), id.visibility(sp)) else View.GONE
                            if (view is TextView) {
                                view.rotation = if (viewjson.has("rotation")) viewjson.getInt("rotation").toFloat() else 0F
                                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ((if (viewjson.has("textsize")) viewjson.getInt("textsize") else 22) * zoomFactor).toFloat())
                                view.gravity = setGravity(if (viewjson.has("gravity")) viewjson.getString("gravity") else "center")
                                view.setTypeface(
                                    setFont(if (viewjson.has("font")) viewjson.getString("font") else "sans-serif"),
                                    setStyle(if (viewjson.has("fontStyle")) viewjson.getString("fontStyle") else "normal")
                                )
                                if (viewjson.has("fontColor"))
                                    view.setTextColor(getColor(viewjson.getString("fontColor")))

                                if (viewjson.has("textvalue"))
                                    view.text = viewjson.getString("textvalue")
                            }

                            if (view is ImageView) {
                                view.clearColorFilter()
                                drawableDataMap[CustomWatchfaceDrawableDataKey.fromKey(id.key)]?.toDrawable(resources)?.also {
                                    if (viewjson.has("color"))
                                        it.colorFilter = changeDrawableColor(getColor(viewjson.getString("color")))
                                    else
                                        it.clearColorFilter()
                                    view.setImageDrawable(it)
                                } ?: apply {
                                    view.setImageDrawable(CustomWatchfaceDrawableDataKey.fromKey(id.key).icon?.let { context.getDrawable(it) })
                                    if (viewjson.has("color"))
                                        view.setColorFilter(getColor(viewjson.getString("color")))
                                    else
                                        view.clearColorFilter()
                                }
                            }
                        } else {
                            view.visibility = View.GONE
                            if (view is TextView) {
                                view.text = ""
                            }
                        }
                    }
                }
                updateSecondVisibility()
            } catch (e:Exception) {
                persistence.store(defaultWatchface(), false) // relaod correct values to avoid crash of watchface
            }
        }
    }

    private fun defaultWatchface(): EventData.ActionSetCustomWatchface {
        val metadata = JSONObject()
            .put(CustomWatchfaceMetadataKey.CWF_NAME.key, getString(info.nightscout.shared.R.string.wear_default_watchface))
            .put(CustomWatchfaceMetadataKey.CWF_FILENAME.key, getString(info.nightscout.shared.R.string.wear_default_watchface))
            .put(CustomWatchfaceMetadataKey.CWF_AUTHOR.key, "Philoul")
            .put(CustomWatchfaceMetadataKey.CWF_CREATED_AT.key, dateUtil.dateString(dateUtil.now()))
            .put(CustomWatchfaceMetadataKey.CWF_VERSION.key, CUSTOM_VERSION)
        val json = JSONObject()
            .put("metadata", metadata)
            .put("highColor", String.format("#%06X", 0xFFFFFF and highColor))
            .put("midColor", String.format("#%06X", 0xFFFFFF and midColor))
            .put("lowColor", String.format("#%06X", 0xFFFFFF and lowColor))
            .put("lowBatColor", String.format("#%06X", 0xFFFFFF and lowBatColor))
            .put("carbColor", String.format("#%06X", 0xFFFFFF and carbColor))
            .put("gridColor", String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put("pointSize",2)
            .put("enableSecond", true)

        binding.mainLayout.forEach { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            CustomViews.fromId(view.id)?.let {
                if (view is TextView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put("width", (params.width / zoomFactor).toInt())
                            .put("height", (params.height / zoomFactor).toInt())
                            .put("topmargin", (params.topMargin / zoomFactor).toInt())
                            .put("leftmargin", (params.leftMargin / zoomFactor).toInt())
                            .put("rotation", view.rotation.toInt())
                            .put("visibility", getVisibility(view.visibility))
                            .put("textsize", view.textSize.toInt())
                            .put("gravity", getGravity(view.gravity))
                            .put("font", getFont(view.typeface))
                            .put("fontStyle", getStyle(view.typeface.style))
                            .put("fontColor", String.format("#%06X", 0xFFFFFF and view.currentTextColor))
                    )
                }
                if (view is ImageView) {
                    //view.backgroundTintList =
                    json.put(
                        it.key,
                        JSONObject()
                            .put("width", (params.width / zoomFactor).toInt())
                            .put("height", (params.height / zoomFactor).toInt())
                            .put("topmargin", (params.topMargin / zoomFactor).toInt())
                            .put("leftmargin", (params.leftMargin / zoomFactor).toInt())
                            .put("visibility", getVisibility(view.visibility))
                    )
                }
                if (view is lecho.lib.hellocharts.view.LineChartView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put("width", (params.width / zoomFactor).toInt())
                            .put("height", (params.height / zoomFactor).toInt())
                            .put("topmargin", (params.topMargin / zoomFactor).toInt())
                            .put("leftmargin", (params.leftMargin / zoomFactor).toInt())
                            .put("visibility", getVisibility(view.visibility))
                    )
                }
            }
        }
        val metadataMap = ZipWatchfaceFormat.loadMetadata(json)
        val drawableDataMap: CustomWatchfaceDrawableDataMap = mutableMapOf()
        getResourceByteArray(info.nightscout.shared.R.drawable.watchface_custom)?.let {
            val drawableData = DrawableData(it,DrawableFormat.PNG)
            drawableDataMap[CustomWatchfaceDrawableDataKey.CUSTOM_WATCHFACE] = drawableData
        }
        return EventData.ActionSetCustomWatchface(CustomWatchfaceData(json.toString(4), metadataMap, drawableDataMap))
    }

    private fun setDefaultColors() {
        highColor = Color.parseColor("#FFFF00")
        midColor = Color.parseColor("#00FF00")
        lowColor = Color.parseColor("#FF0000")
        carbColor = ContextCompat.getColor(this, R.color.carbs)
        lowBatColor = ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
        gridColor = Color.WHITE
    }

    private fun setVisibility(visibility: String, pref: Boolean = true): Int = when (visibility) {
        "visible"   -> pref.toVisibility()
        "invisible" -> pref.toVisibilityKeepSpace()
        "gone"      -> View.GONE
        else        -> View.GONE
    }

    private fun getVisibility(visibility: Int): String = when (visibility) {
        View.VISIBLE   -> "visible"
        View.INVISIBLE -> "invisible"
        View.GONE      -> "gone"
        else           -> "gone"
    }

    private fun setGravity(gravity: String): Int = when (gravity) {
        "center" -> Gravity.CENTER
        "left"   -> Gravity.LEFT
        "right"  -> Gravity.RIGHT
        else     -> Gravity.CENTER
    }

    private fun getGravity(gravity: Int): String = when (gravity) {
        Gravity.CENTER -> "center"
        Gravity.LEFT   -> "left"
        Gravity.RIGHT  -> "right"
        else           -> "center"
    }

    private fun setFont(font: String): Typeface = when (font) {
        "sans-serif"               -> Typeface.SANS_SERIF
        "default"                  -> Typeface.DEFAULT
        "default-bold"             -> Typeface.DEFAULT_BOLD
        "monospace"                -> Typeface.MONOSPACE
        "serif"                    -> Typeface.SERIF
        "roboto-condensed-bold"    -> ResourcesCompat.getFont(context, R.font.roboto_condensed_bold)!!
        "roboto-condensed-light"   -> ResourcesCompat.getFont(context, R.font.roboto_condensed_light)!!
        "roboto-condensed-regular" -> ResourcesCompat.getFont(context, R.font.roboto_condensed_regular)!!
        "roboto-slab-light"        -> ResourcesCompat.getFont(context, R.font.roboto_slab_light)!!
        else                       -> Typeface.DEFAULT
    }

    private fun getFont(font: Typeface): String = when (font) {
        Typeface.SANS_SERIF                                                 -> "sans-serif"
        Typeface.DEFAULT                                                    -> "default"
        Typeface.DEFAULT_BOLD                                               -> "default-bold"
        Typeface.MONOSPACE                                                  -> "monospace"
        Typeface.SERIF                                                      -> "serif"
        ResourcesCompat.getFont(context, R.font.roboto_condensed_bold)!!    -> "roboto-condensed-bold"
        ResourcesCompat.getFont(context, R.font.roboto_condensed_light)!!   -> "roboto-condensed-light"
        ResourcesCompat.getFont(context, R.font.roboto_condensed_regular)!! -> "roboto-condensed-regular"
        ResourcesCompat.getFont(context, R.font.roboto_slab_light)!!        -> "roboto-slab-light"
        else                                                                -> "default"
    }

    private fun setStyle(style: String): Int = when (style) {
        "normal"      -> Typeface.NORMAL
        "bold"        -> Typeface.BOLD
        "bold-italic" -> Typeface.BOLD_ITALIC
        "italic"      -> Typeface.ITALIC
        else          -> Typeface.NORMAL
    }

    private fun getStyle(style: Int): String = when (style) {
        Typeface.NORMAL      -> "normal"
        Typeface.BOLD        -> "bold"
        Typeface.BOLD_ITALIC -> "bold-italic"
        Typeface.ITALIC      -> "italic"
        else                 -> "normal"
    }

    fun getResourceByteArray(resourceId: Int): ByteArray? {
        val inputStream = resources.openRawResource(resourceId)
        val byteArrayOutputStream = ByteArrayOutputStream()

        try {
            val buffer = ByteArray(1024)
            var count: Int
            while (inputStream.read(buffer).also { count = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, count)
            }
            byteArrayOutputStream.close()
            inputStream.close()

            return byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
        }
        return null
    }

    private fun changeDrawableColor(color: Int): ColorFilter {
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        colorMatrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    Color.red(color) / 255f, 0f, 0f, 0f, 0f,
                    0f, Color.green(color) / 255f, 0f, 0f, 0f,
                    0f, 0f, Color.blue(color) / 255f, 0f, 0f,
                    0f, 0f, 0f, Color.alpha(color) / 255f, 0f
                )
            )
        )
        return ColorMatrixColorFilter(colorMatrix)
    }

    private fun getColor(color: String): Int {
        if (color == "bgColor")
            return bgColor
        else
            return try {
                Color.parseColor(color)
            } catch (e: Exception) {
                Color.GRAY
            }
    }

    enum class CustomViews(val key: String, @IdRes val id: Int, @StringRes val pref: Int?) {

        BACKGROUND(CustomWatchfaceDrawableDataKey.BACKGROUND.key, R.id.background, null),
        CHART("chart", R.id.chart, null),
        COVER_CHART(CustomWatchfaceDrawableDataKey.COVERCHART.key, R.id.cover_chart, null),
        FREETEXT1("freetext1", R.id.freetext1, null),
        FREETEXT2("freetext2", R.id.freetext2, null),
        IOB1("iob1", R.id.iob1, R.string.key_show_iob),
        IOB2("iob2", R.id.iob2, R.string.key_show_iob),
        COB1("cob1", R.id.cob1, R.string.key_show_cob),
        COB2("cob2", R.id.cob2, R.string.key_show_cob),
        DELTA("delta", R.id.delta, R.string.key_show_delta),
        AVG_DELTA("avg_delta", R.id.avg_delta, R.string.key_show_avg_delta),
        UPLOADER_BATTERY("uploader_battery", R.id.uploader_battery, R.string.key_show_uploader_battery),
        RIG_BATTERY("rig_battery", R.id.rig_battery, R.string.key_show_rig_battery),
        BASALRATE("basalRate", R.id.basalRate, R.string.key_show_temp_basal),
        BGI("bgi", R.id.bgi, null),
        TIME("time", R.id.time, null),
        HOUR("hour", R.id.hour, null),
        MINUTE("minute", R.id.minute, null),
        SECOND("second", R.id.second, R.string.key_show_seconds),
        TIMEPERIOD("timePeriod", R.id.timePeriod, null),
        DAY_NAME("day_name", R.id.day_name, null),
        DAY("day", R.id.day, null),
        MONTH("month", R.id.month, null),
        LOOP("loop", R.id.loop, R.string.key_show_external_status),
        DIRECTION("direction", R.id.direction, R.string.key_show_direction),
        DIRECTION2("direction2", R.id.direction2, R.string.key_show_direction),
        TIMESTAMP("timestamp", R.id.timestamp, R.string.key_show_ago),
        SGV("sgv", R.id.sgv, R.string.key_show_bg),
        COVER_PLATE(CustomWatchfaceDrawableDataKey.COVERPLATE.key, R.id.cover_plate, null),
        HOUR_HABD(CustomWatchfaceDrawableDataKey.HOURHAND.key, R.id.hour_hand, null),
        MINUTE_HAND(CustomWatchfaceDrawableDataKey.MINUTEHAND.key, R.id.minute_hand, null),
        SECOND_HAND(CustomWatchfaceDrawableDataKey.SECONDHAND.key, R.id.second_hand, R.string.key_show_seconds);

        companion object {

            private val keyToEnumMap = HashMap<String, CustomViews>()
            private val idToEnumMap = HashMap<Int, CustomViews>()

            init {
                for (value in values()) keyToEnumMap[value.key] = value
                for (value in values()) idToEnumMap[value.id] = value
            }

            fun fromKey(key: String): CustomViews? =
                if (keyToEnumMap.containsKey(key)) {
                    keyToEnumMap[key]
                } else {
                    null
                }
            fun fromId(id: Int): CustomViews? =
                if (idToEnumMap.containsKey(id)) {
                    idToEnumMap[id]
                } else {
                    null
                }
        }


        fun visibility(sp: SP): Boolean = this.pref?.let { sp.getBoolean(it, true) }
            ?: true
    }


    enum class TrendArrow(val text: String, val symbol: String,@DrawableRes val icon: Int) {
        NONE("NONE", "??", R.drawable.ic_invalid),
        TRIPLE_UP("TripleUp", "X", R.drawable.ic_invalid),
        DOUBLE_UP("DoubleUp", "\u21c8", R.drawable.ic_doubleup),
        SINGLE_UP("SingleUp", "\u2191", R.drawable.ic_singleup),
        FORTY_FIVE_UP("FortyFiveUp", "\u2197", R.drawable.ic_fortyfiveup),
        FLAT("Flat", "\u2192", R.drawable.ic_flat),
        FORTY_FIVE_DOWN("FortyFiveDown", "\u2198",R.drawable.ic_fortyfivedown),
        SINGLE_DOWN("SingleDown", "\u2193", R.drawable.ic_singledown),
        DOUBLE_DOWN("DoubleDown", "\u21ca", R.drawable.ic_doubledown),
        TRIPLE_DOWN("TripleDown", "X",R.drawable.ic_invalid)
        ;

        companion object {
            fun fromSymbol(direction: String?) =
                values().firstOrNull { it.symbol == direction } ?: NONE
        }
    }

}
