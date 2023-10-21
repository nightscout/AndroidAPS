@file:Suppress("DEPRECATION")

package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import androidx.annotation.FontRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.forEach
import androidx.viewbinding.ViewBinding
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.CUSTOM_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.CwfMetadataMap
import app.aaps.core.interfaces.rx.weardata.CwfResDataMap
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.JsonKeyValues
import app.aaps.core.interfaces.rx.weardata.JsonKeys
import app.aaps.core.interfaces.rx.weardata.JsonKeys.*
import app.aaps.core.interfaces.rx.weardata.ResData
import app.aaps.core.interfaces.rx.weardata.ResFileMap
import app.aaps.core.interfaces.rx.weardata.ResFormat
import app.aaps.core.interfaces.rx.weardata.ViewKeys
import app.aaps.core.interfaces.rx.weardata.ZipWatchfaceFormat
import app.aaps.core.interfaces.rx.weardata.isEquals
import app.aaps.wear.R
import app.aaps.wear.databinding.ActivityCustomBinding
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import org.joda.time.DateTime
import org.joda.time.TimeOfDay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.floor

@SuppressLint("UseCompatLoadingForDrawables")
class CustomWatchface : BaseWatchFace() {

    @Inject lateinit var context: Context
    private lateinit var binding: ActivityCustomBinding
    private var zoomFactor = 1.0
    private val displaySize = Point()
    private val templeResolution = 400
    private var lowBatColor = Color.RED
    private var resDataMap: CwfResDataMap = mutableMapOf()
    private var json = JSONObject()
    private var jsonString = ""
    private val bgColor: Int
        get() = when (singleBg.sgvLevel) {
            1L   -> highColor
            0L   -> midColor
            -1L  -> lowColor
            else -> midColor
        }

    @Suppress("DEPRECATION")
    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityCustomBinding.inflate(inflater)
        setDefaultColors()
        persistence.store(defaultWatchface(), true)
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(displaySize)
        zoomFactor = (displaySize.x).toDouble() / templeResolution.toDouble()
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
        binding.direction2.setImageDrawable(TrendArrowMap.drawable())
        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
        // rotate the minute hand.
        binding.minuteHand.rotation = TimeOfDay().minuteOfHour * 6f
        // rotate the hour hand.
        binding.hourHand.rotation = TimeOfDay().hourOfDay * 30f + TimeOfDay().minuteOfHour * 0.5f
    }

    override fun setColorDark() {
        setWatchfaceStyle()
        if ((ViewMap.SGV.dynData?.stepFontColor ?: 0) <= 0)
            binding.sgv.setTextColor(bgColor)
        if ((ViewMap.DIRECTION.dynData?.stepColor ?: 0) <= 0)
            binding.direction2.colorFilter = changeDrawableColor(bgColor)
        if (ageLevel != 1 && (ViewMap.TIMESTAMP.dynData?.stepFontColor ?: 0) <= 0)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if (status.batteryLevel != 1 && (ViewMap.UPLOADER_BATTERY.dynData?.stepFontColor ?: 0) <= 0)
            binding.uploaderBattery.setTextColor(lowBatColor)
        if ((ViewMap.LOOP.dynData?.stepDraw ?: 0) <= 0)     // Apply automatic background image only if no dynData or no step images
            when (loopLevel) {
                -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
                else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
            }

        setupCharts()
    }

    override fun setColorBright() {
        setColorDark()
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setSecond() {
        binding.time.text = if (showSecond)
            getString(R.string.hour_minute_second, dateUtil.hourString(), dateUtil.minuteString(), dateUtil.secondString())
        else
            getString(R.string.hour_minute, dateUtil.hourString(), dateUtil.minuteString())
        binding.second.text = dateUtil.secondString()
        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
    }

    override fun updateSecondVisibility() {
        binding.second.visibility = (binding.second.visibility == View.VISIBLE && showSecond).toVisibility()
        binding.secondHand.visibility = (binding.secondHand.visibility == View.VISIBLE && showSecond).toVisibility()
    }

    private fun setWatchfaceStyle() {
        val customWatchface = persistence.readCustomWatchface() ?: persistence.readCustomWatchface(true)
        customWatchface?.let {
            updatePref(it.customWatchfaceData.metadata)
            try {
                json = JSONObject(it.customWatchfaceData.json)
                if (!resDataMap.isEquals(it.customWatchfaceData.resDatas) || jsonString != it.customWatchfaceData.json) {
                    resDataMap = it.customWatchfaceData.resDatas
                    jsonString = it.customWatchfaceData.json
                    DynProvider.init(this, json)
                    FontMap.init(this)
                    ViewMap.init(this)
                    TrendArrowMap.init(this)
                    binding.freetext1.text = ""
                    binding.freetext2.text = ""
                    binding.freetext3.text = ""
                    binding.freetext4.text = ""
                }
                if (checkPref()) {
                    DynProvider.init(this, json)
                }

                enableSecond = json.optBoolean(ENABLESECOND.key) && sp.getBoolean(R.string.key_show_seconds, true)
                pointSize = json.optInt(POINTSIZE.key, 2)
                dayNameFormat = json.optString(DAYNAMEFORMAT.key, "E").takeIf { it.matches(Regex("E{1,4}")) } ?: "E"
                monthFormat = json.optString(MONTHFORMAT.key, "MMM").takeIf { it.matches(Regex("M{1,4}")) } ?: "MMM"
                binding.dayName.text = dateUtil.dayNameString(dayNameFormat).substringBeforeLast(".") // Update dayName and month according to format on cwf loading
                binding.month.text = dateUtil.monthString(monthFormat).substringBeforeLast(".")
                val jsonColor = dynPref[json.optString(DYNPREFCOLOR.key)] ?: json
                highColor = getColor(jsonColor.optString(HIGHCOLOR.key), ContextCompat.getColor(this, R.color.dark_highColor))
                midColor = getColor(jsonColor.optString(MIDCOLOR.key), ContextCompat.getColor(this, R.color.inrange))
                lowColor = getColor(jsonColor.optString(LOWCOLOR.key), ContextCompat.getColor(this, R.color.low))
                lowBatColor = getColor(jsonColor.optString(LOWBATCOLOR.key), ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))
                carbColor = getColor(jsonColor.optString(CARBCOLOR.key), ContextCompat.getColor(this, R.color.carbs))
                basalBackgroundColor = getColor(jsonColor.optString(BASALBACKGROUNDCOLOR.key), ContextCompat.getColor(this, R.color.basal_dark))
                basalCenterColor = getColor(jsonColor.optString(BASALCENTERCOLOR.key), ContextCompat.getColor(this, R.color.basal_light))
                gridColor = getColor(jsonColor.optString(GRIDCOLOR.key), Color.WHITE)

                binding.mainLayout.forEach { view ->
                    ViewMap.fromId(view.id)?.let { viewMap ->
                        when (view) {
                            is TextView                                 -> viewMap.customizeTextView(view)
                            is ImageView                                -> viewMap.customizeImageView(view)
                            is lecho.lib.hellocharts.view.LineChartView -> viewMap.customizeGraphView(view)
                            else                                        -> viewMap.customizeViewCommon(view)
                        }
                    }
                }
                manageSpecificViews()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.WEAR, "Crash during Custom watch load")
                persistence.store(defaultWatchface(), false) // relaod correct values to avoid crash of watchface
            }
        }
    }

    private fun updatePref(metadata: CwfMetadataMap) {
        val cwfAuthorization = metadata[CwfMetadataKey.CWF_AUTHORIZATION]?.toBooleanStrictOrNull()
        cwfAuthorization?.let { authorization ->
            if (authorization) {
                PrefMap.values().forEach { pref ->
                    metadata[CwfMetadataKey.fromKey(pref.key)]?.toBooleanStrictOrNull()?.let { sp.putBoolean(pref.prefKey, it) }
                }
            }
        }
    }

    private fun defaultWatchface(): EventData.ActionSetCustomWatchface {
        val metadata = JSONObject()
            .put(CwfMetadataKey.CWF_NAME.key, getString(app.aaps.core.interfaces.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_FILENAME.key, getString(app.aaps.core.interfaces.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_AUTHOR.key, "Philoul")
            .put(CwfMetadataKey.CWF_CREATED_AT.key, dateUtil.dateString(dateUtil.now()))
            .put(CwfMetadataKey.CWF_AUTHOR_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_COMMENT.key, getString(app.aaps.core.interfaces.R.string.default_custom_watchface_comment))
        val json = JSONObject()
            .put(METADATA.key, metadata)
            .put(HIGHCOLOR.key, String.format("#%06X", 0xFFFFFF and highColor))
            .put(MIDCOLOR.key, String.format("#%06X", 0xFFFFFF and midColor))
            .put(LOWCOLOR.key, String.format("#%06X", 0xFFFFFF and lowColor))
            .put(LOWBATCOLOR.key, String.format("#%06X", 0xFFFFFF and lowBatColor))
            .put(CARBCOLOR.key, String.format("#%06X", 0xFFFFFF and carbColor))
            .put(BASALBACKGROUNDCOLOR.key, String.format("#%06X", 0xFFFFFF and basalBackgroundColor))
            .put(BASALCENTERCOLOR.key, String.format("#%06X", 0xFFFFFF and basalCenterColor))
            .put(GRIDCOLOR.key, String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put(POINTSIZE.key, 2)
            .put(ENABLESECOND.key, true)

        binding.mainLayout.forEach { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            ViewMap.fromId(view.id)?.let {
                if (view is TextView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put(WIDTH.key, (params.width / zoomFactor).toInt())
                            .put(HEIGHT.key, (params.height / zoomFactor).toInt())
                            .put(TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                            .put(LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                            .put(ROTATION.key, view.rotation.toInt())
                            .put(VISIBILITY.key, getVisibility(view.visibility))
                            .put(TEXTSIZE.key, view.textSize.toInt())
                            .put(GRAVITY.key, GravityMap.key(view.gravity))
                            .put(FONT.key, FontMap.key())
                            .put(FONTSTYLE.key, StyleMap.key(view.typeface.style))
                            .put(FONTCOLOR.key, String.format("#%06X", 0xFFFFFF and view.currentTextColor))
                    )
                }
                if (view is ImageView || view is lecho.lib.hellocharts.view.LineChartView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put(WIDTH.key, (params.width / zoomFactor).toInt())
                            .put(HEIGHT.key, (params.height / zoomFactor).toInt())
                            .put(TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                            .put(LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                            .put(VISIBILITY.key, getVisibility(view.visibility))
                    )
                }
            }
        }
        val metadataMap = ZipWatchfaceFormat.loadMetadata(json)
        val drawableDataMap: CwfResDataMap = mutableMapOf()
        getResourceByteArray(R.drawable.watchface_custom)?.let {
            drawableDataMap[ResFileMap.CUSTOM_WATCHFACE.fileName] = ResData(it, ResFormat.PNG)
        }
        return EventData.ActionSetCustomWatchface(CwfData(json.toString(4), metadataMap, drawableDataMap))
    }

    private fun setDefaultColors() {
        highColor = Color.parseColor("#FFFF00")
        midColor = Color.parseColor("#00FF00")
        lowColor = Color.parseColor("#FF0000")
        carbColor = ContextCompat.getColor(this, R.color.carbs)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        lowBatColor = ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
        gridColor = Color.WHITE
    }

    private fun setVisibility(visibility: String, pref: Boolean = true): Int = when (visibility) {
        JsonKeyValues.VISIBLE.key -> pref.toVisibility()
        else                      -> View.GONE
    }

    private fun getVisibility(visibility: Int): String = when (visibility) {
        View.VISIBLE -> JsonKeyValues.VISIBLE.key
        else         -> JsonKeyValues.GONE.key
    }

    private fun getResourceByteArray(resourceId: Int): ByteArray? {
        val inputStream = resources.openRawResource(resourceId)
        val byteArrayOutputStream = ByteArrayOutputStream()

        val buffer = ByteArray(1024)
        var count: Int
        while (inputStream.read(buffer).also { count = it } != -1) {
            byteArrayOutputStream.write(buffer, 0, count)
        }
        byteArrayOutputStream.close()
        inputStream.close()

        return byteArrayOutputStream.toByteArray()
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

    private fun getColor(color: String, defaultColor: Int = Color.GRAY): Int =
        if (color == JsonKeyValues.BGCOLOR.key)
            bgColor
        else
            try {
                Color.parseColor(color)
            } catch (e: Exception) {
                defaultColor
            }

    private fun manageSpecificViews() {
        //Background should fill all the watchface and must be visible
        val params = FrameLayout.LayoutParams((templeResolution * zoomFactor).toInt(), (templeResolution * zoomFactor).toInt())
        params.topMargin = 0
        params.leftMargin = 0
        binding.background.layoutParams = params
        binding.background.visibility = View.VISIBLE
        updateSecondVisibility()
        setSecond() // Update second visibility for time view
        binding.timePeriod.visibility = (binding.timePeriod.visibility == View.VISIBLE && android.text.format.DateFormat.is24HourFormat(this).not()).toVisibility()
    }

    private enum class ViewMap(
        val key: String,
        @IdRes val id: Int,
        @StringRes val pref: Int? = null,
        @IdRes val defaultDrawable: Int? = null,
        val customDrawable: ResFileMap? = null,
        val customHigh: ResFileMap? = null,
        val customLow: ResFileMap? = null
    ) {

        BACKGROUND(
            key = ViewKeys.BACKGROUND.key,
            id = R.id.background,
            defaultDrawable = R.drawable.background,
            customDrawable = ResFileMap.BACKGROUND,
            customHigh = ResFileMap.BACKGROUND_HIGH,
            customLow = ResFileMap.BACKGROUND_LOW
        ),
        CHART(ViewKeys.CHART.key, R.id.chart),
        COVER_CHART(
            key = ViewKeys.COVER_CHART.key,
            id = R.id.cover_chart,
            customDrawable = ResFileMap.COVER_CHART,
            customHigh = ResFileMap.COVER_CHART_HIGH,
            customLow = ResFileMap.COVER_CHART_LOW
        ),
        FREETEXT1(ViewKeys.FREETEXT1.key, R.id.freetext1),
        FREETEXT2(ViewKeys.FREETEXT2.key, R.id.freetext2),
        FREETEXT3(ViewKeys.FREETEXT3.key, R.id.freetext3),
        FREETEXT4(ViewKeys.FREETEXT4.key, R.id.freetext4),
        IOB1(ViewKeys.IOB1.key, R.id.iob1, R.string.key_show_iob),
        IOB2(ViewKeys.IOB2.key, R.id.iob2, R.string.key_show_iob),
        COB1(ViewKeys.COB1.key, R.id.cob1, R.string.key_show_cob),
        COB2(ViewKeys.COB2.key, R.id.cob2, R.string.key_show_cob),
        DELTA(ViewKeys.DELTA.key, R.id.delta, R.string.key_show_delta),
        AVG_DELTA(ViewKeys.AVG_DELTA.key, R.id.avg_delta, R.string.key_show_avg_delta),
        UPLOADER_BATTERY(ViewKeys.UPLOADER_BATTERY.key, R.id.uploader_battery, R.string.key_show_uploader_battery),
        RIG_BATTERY(ViewKeys.RIG_BATTERY.key, R.id.rig_battery, R.string.key_show_rig_battery),
        BASALRATE(ViewKeys.BASALRATE.key, R.id.basalRate, R.string.key_show_temp_basal),
        BGI(ViewKeys.BGI.key, R.id.bgi, R.string.key_show_bgi),
        TIME(ViewKeys.TIME.key, R.id.time),
        HOUR(ViewKeys.HOUR.key, R.id.hour),
        MINUTE(ViewKeys.MINUTE.key, R.id.minute),
        SECOND(ViewKeys.SECOND.key, R.id.second, R.string.key_show_seconds),
        TIMEPERIOD(ViewKeys.TIMEPERIOD.key, R.id.timePeriod),
        DAY_NAME(ViewKeys.DAY_NAME.key, R.id.day_name, R.string.key_show_date),
        DAY(ViewKeys.DAY.key, R.id.day, R.string.key_show_date),
        WEEKNUMBER(ViewKeys.WEEKNUMBER.key, R.id.week_number, R.string.key_show_week_number),
        MONTH(ViewKeys.MONTH.key, R.id.month, R.string.key_show_date),
        LOOP(ViewKeys.LOOP.key, R.id.loop, R.string.key_show_external_status),
        DIRECTION(ViewKeys.DIRECTION.key, R.id.direction2, R.string.key_show_direction),
        TIMESTAMP(ViewKeys.TIMESTAMP.key, R.id.timestamp, R.string.key_show_ago),
        SGV(ViewKeys.SGV.key, R.id.sgv, R.string.key_show_bg),
        COVER_PLATE(
            key = ViewKeys.COVER_PLATE.key,
            id = R.id.cover_plate,
            defaultDrawable = R.drawable.simplified_dial,
            customDrawable = ResFileMap.COVER_PLATE,
            customHigh = ResFileMap.COVER_PLATE_HIGH,
            customLow = ResFileMap.COVER_PLATE_LOW
        ),
        HOUR_HAND(
            key = ViewKeys.HOUR_HAND.key,
            id = R.id.hour_hand,
            defaultDrawable = R.drawable.hour_hand,
            customDrawable = ResFileMap.HOUR_HAND,
            customHigh = ResFileMap.HOUR_HAND_HIGH,
            customLow = ResFileMap.HOUR_HAND_LOW
        ),
        MINUTE_HAND(
            key = ViewKeys.MINUTE_HAND.key,
            id = R.id.minute_hand,
            defaultDrawable = R.drawable.minute_hand,
            customDrawable = ResFileMap.MINUTE_HAND,
            customHigh = ResFileMap.MINUTE_HAND_HIGH,
            customLow = ResFileMap.MINUTE_HAND_LOW
        ),
        SECOND_HAND(
            key = ViewKeys.SECOND_HAND.key,
            id = R.id.second_hand,
            pref = R.string.key_show_seconds,
            defaultDrawable = R.drawable.second_hand,
            customDrawable = ResFileMap.SECOND_HAND,
            customHigh = ResFileMap.SECOND_HAND_HIGH,
            customLow = ResFileMap.SECOND_HAND_LOW
        );

        companion object {

            const val TRANSPARENT = "#00000000"
            fun init(cwf: CustomWatchface) = values().forEach {
                it.cwf = cwf
                // reset all customized drawable when new watchface is loaded
                it.rangeCustom = null
                it.highCustom = null
                it.lowCustom = null
                it.textDrawable = null
                it.viewJson = null
                it.twinView = null
            }

            fun fromId(id: Int): ViewMap? = values().firstOrNull { it.id == id }
            fun fromKey(key: String?): ViewMap? = values().firstOrNull { it.key == key }
        }

        lateinit var cwf: CustomWatchface
        var width = 0
        var height = 0
        var left = 0
        var top = 0
        var viewJson: JSONObject? = null
            get() = field ?: cwf.json.optJSONObject(key)?.also { viewJson = it }
        val visibility: Int
            get() = viewJson?.let { cwf.setVisibility(it.optString(VISIBILITY.key, JsonKeyValues.GONE.key), visibility()) } ?: View.GONE
        var dynData: DynProvider? = null
        var rangeCustom: Drawable? = null
            get() = field ?: customDrawable?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { rangeCustom = it } }
        var highCustom: Drawable? = null
            get() = field ?: customHigh?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { highCustom = it } }
        var lowCustom: Drawable? = null
            get() = field ?: customLow?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { lowCustom = it } }
        var textDrawable: Drawable? = null
        val drawable: Drawable?
            get() = dynData?.getDrawable() ?: when (cwf.singleBg.sgvLevel) {
                1L   -> highCustom ?: rangeCustom
                0L   -> rangeCustom
                -1L  -> lowCustom ?: rangeCustom
                else -> rangeCustom
            }
        var twinView: ViewMap? = null
            get() = field ?: viewJson?.let { viewJson -> ViewMap.fromKey(viewJson.optString(TWINVIEW.key)).also { twinView = it } }

        fun visibility(): Boolean = this.pref?.let { cwf.sp.getBoolean(it, true) }
            ?: true

        fun textDrawable(): Drawable? = textDrawable
            ?: cwf.resDataMap[viewJson?.optString(JsonKeys.BACKGROUND.key)]?.toDrawable(cwf.resources, width, height)?.also { textDrawable = it }

        fun customizeViewCommon(view: View) {
            view.visibility = visibility
            viewJson?.let { viewJson ->
                width = (viewJson.optInt(WIDTH.key) * cwf.zoomFactor).toInt()
                height = (viewJson.optInt(HEIGHT.key) * cwf.zoomFactor).toInt()
                left = (viewJson.optInt(LEFTMARGIN.key) * cwf.zoomFactor).toInt()
                top = (viewJson.optInt(TOPMARGIN.key) * cwf.zoomFactor).toInt()
                val params = FrameLayout.LayoutParams(width, height)
                dynData = DynProvider.getDyn(cwf, viewJson.optString(DYNPREF.key), viewJson.optString(DYNDATA.key), width, height, key)
                val topOffset = ((if (viewJson.optBoolean(TOPOFFSET.key, false)) dynData?.getTopOffset() ?: 0 else 0) * cwf.zoomFactor).toInt()
                val topOffsetTwin = ((twinView?.let { if (it.visibility != View.VISIBLE) viewJson.optInt(TOPOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val topOffsetStep = ((dynData?.getTopOffsetStep() ?:0)  * cwf.zoomFactor).toInt()
                params.topMargin = top + topOffset + topOffsetTwin + topOffsetStep
                val leftOffset = ((if (viewJson.optBoolean(LEFTOFFSET.key, false)) dynData?.getLeftOffset() ?: 0 else 0) * cwf.zoomFactor).toInt()
                val leftOffsetTwin = ((twinView?.let { if (it.visibility != View.VISIBLE) viewJson.optInt(LEFTOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val leftOffsetStep = ((dynData?.getLeftOffsetStep() ?:0)  * cwf.zoomFactor).toInt()
                params.leftMargin = left + leftOffset + leftOffsetTwin + leftOffsetStep
                view.layoutParams = params
                val rotationOffset = if (viewJson.optBoolean(ROTATIONOFFSET.key, false)) dynData?.getRotationOffset()?.toFloat() ?: 0F else 0F
                val rotationOffsetStep = (dynData?.getRotationOffsetStep() ?:0).toFloat()
                view.rotation = viewJson.optInt(ROTATION.key).toFloat() + rotationOffset + rotationOffsetStep
            }
        }

        fun customizeTextView(view: TextView) {
            customizeViewCommon(view)
            viewJson?.let { viewJson ->
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ((dynData?.getTextSizeStep() ?: viewJson.optInt(TEXTSIZE.key, 22)) * cwf.zoomFactor).toFloat())
                view.gravity = GravityMap.gravity(viewJson.optString(GRAVITY.key, GravityMap.CENTER.key))
                view.setTypeface(
                    FontMap.font(viewJson.optString(FONT.key, FontMap.DEFAULT.key)),
                    StyleMap.style(viewJson.optString(FONTSTYLE.key, StyleMap.NORMAL.key))
                )
                view.setTextColor(dynData?.getFontColorStep() ?: cwf.getColor(viewJson.optString(FONTCOLOR.key)))
                view.isAllCaps = viewJson.optBoolean(ALLCAPS.key)
                if (viewJson.has(TEXTVALUE.key))
                    view.text = viewJson.optString(TEXTVALUE.key)
                (dynData?.getDrawable() ?: textDrawable())?.let {
                    if (viewJson.has(COLOR.key) || (dynData?.stepColor ?: 0) > 0)           // Note only works on bitmap (png or jpg)  not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                    // if no drawable loaded either background key or dynData, then apply color to text background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key, TRANSPARENT), Color.TRANSPARENT))
                }
            } ?: apply { view.text = "" }
        }

        fun customizeImageView(view: ImageView) {
            customizeViewCommon(view)
            view.clearColorFilter()
            viewJson?.let { viewJson ->
                drawable?.let {
                    if (viewJson.has(COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.setImageDrawable(it)
                } ?: apply {
                    view.setImageDrawable(defaultDrawable?.let { cwf.resources.getDrawable(it) })
                    if (viewJson.has(COLOR.key) || (dynData?.stepColor ?: 0) > 0)       // works on xml included into res files
                        view.setColorFilter(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key)))
                    else
                        view.clearColorFilter()
                }
                if (view.drawable == null)                                              // if no drowable (either default, hardcoded or dynData, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key, TRANSPARENT), Color.TRANSPARENT))
            }
        }

        fun customizeGraphView(view: lecho.lib.hellocharts.view.LineChartView) {
            customizeViewCommon(view)
            viewJson?.let { viewJson ->
                (dynData?.getDrawable() ?: textDrawable())?.let {
                    if (viewJson.has(COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                // if no drowable loaded, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(COLOR.key, TRANSPARENT), Color.TRANSPARENT))
                }
            }
        }
    }

    private enum class TrendArrowMap(val symbol: String, @DrawableRes val icon: Int, val customDrawable: ResFileMap?, val dynValue: Double) {
        NONE("??", R.drawable.ic_invalid, ResFileMap.ARROW_NONE, 0.0),
        TRIPLE_UP("X", R.drawable.ic_doubleup, ResFileMap.ARROW_DOUBLE_UP, 7.0),
        DOUBLE_UP("\u21c8", R.drawable.ic_doubleup, ResFileMap.ARROW_DOUBLE_UP, 7.0),
        SINGLE_UP("\u2191", R.drawable.ic_singleup, ResFileMap.ARROW_SINGLE_UP, 6.0),
        FORTY_FIVE_UP("\u2197", R.drawable.ic_fortyfiveup, ResFileMap.ARROW_FORTY_FIVE_UP, 5.0),
        FLAT("\u2192", R.drawable.ic_flat, ResFileMap.ARROW_FLAT, 4.0),
        FORTY_FIVE_DOWN("\u2198", R.drawable.ic_fortyfivedown, ResFileMap.ARROW_FORTY_FIVE_DOWN, 3.0),
        SINGLE_DOWN("\u2193", R.drawable.ic_singledown, ResFileMap.ARROW_SINGLE_DOWN, 2.0),
        DOUBLE_DOWN("\u21ca", R.drawable.ic_doubledown, ResFileMap.ARROW_DOUBLE_DOWN, 1.0),
        TRIPLE_DOWN("X", R.drawable.ic_doubledown, ResFileMap.ARROW_DOUBLE_DOWN, 1.0);

        companion object {

            fun init(cwf: CustomWatchface) = values().forEach {
                it.cwf = cwf
                it.arrowCustom = null

            }

            fun drawable() = values().firstOrNull { it.symbol == it.cwf.singleBg.slopeArrow }?.arrowCustom ?: NONE.arrowCustom
            fun value() = values().firstOrNull { it.symbol == it.cwf.singleBg.slopeArrow }?.dynValue ?: NONE.dynValue
        }

        lateinit var cwf: CustomWatchface
        var arrowCustom: Drawable? = null
            get() = field ?: customDrawable?.let { cwf.resDataMap[it.fileName]?.toDrawable(cwf.resources)?.also { arrowCustom = it } } ?: cwf.resources.getDrawable(icon)
    }

    @SuppressLint("RtlHardcoded")
    private enum class GravityMap(val key: String, val gravity: Int) {

        CENTER(JsonKeyValues.CENTER.key, Gravity.CENTER),
        LEFT(JsonKeyValues.LEFT.key, Gravity.LEFT),
        RIGHT(JsonKeyValues.RIGHT.key, Gravity.RIGHT);

        companion object {

            fun gravity(key: String?) = values().firstOrNull { it.key == key }?.gravity ?: CENTER.gravity
            fun key(gravity: Int) = values().firstOrNull { it.gravity == gravity }?.key ?: CENTER.key
        }
    }

    private enum class FontMap(val key: String, var font: Typeface, @FontRes val fontRessources: Int? = null) {
        SANS_SERIF(JsonKeyValues.SANS_SERIF.key, Typeface.SANS_SERIF),
        DEFAULT(JsonKeyValues.DEFAULT.key, Typeface.DEFAULT),
        DEFAULT_BOLD(JsonKeyValues.DEFAULT_BOLD.key, Typeface.DEFAULT_BOLD),
        MONOSPACE(JsonKeyValues.MONOSPACE.key, Typeface.MONOSPACE),
        SERIF(JsonKeyValues.SERIF.key, Typeface.SERIF),
        ROBOTO_CONDENSED_BOLD(JsonKeyValues.ROBOTO_CONDENSED_BOLD.key, Typeface.DEFAULT, R.font.roboto_condensed_bold),
        ROBOTO_CONDENSED_LIGHT(JsonKeyValues.ROBOTO_CONDENSED_LIGHT.key, Typeface.DEFAULT, R.font.roboto_condensed_light),
        ROBOTO_CONDENSED_REGULAR(JsonKeyValues.ROBOTO_CONDENSED_REGULAR.key, Typeface.DEFAULT, R.font.roboto_condensed_regular),
        ROBOTO_SLAB_LIGHT(JsonKeyValues.ROBOTO_SLAB_LIGHT.key, Typeface.DEFAULT, R.font.roboto_slab_light);

        companion object {

            private val customFonts = mutableMapOf<String, Typeface>()
            fun init(cwf: CustomWatchface) {
                customFonts.clear()
                values().forEach { fontMap ->
                    customFonts[fontMap.key.lowercase()] = fontMap.fontRessources?.let { fontResource ->
                        ResourcesCompat.getFont(cwf.context, fontResource)
                    } ?: fontMap.font
                }
                cwf.resDataMap.filter { (_, resData) ->
                    resData.format == ResFormat.TTF || resData.format == ResFormat.OTF
                }.forEach { (key, resData) ->
                    customFonts[key.lowercase()] = resData.toTypeface() ?: Typeface.DEFAULT
                }
            }

            fun font(key: String) = customFonts[key.lowercase()] ?: DEFAULT.font
            fun key() = DEFAULT.key
        }
    }

    private enum class StyleMap(val key: String, val style: Int) {
        NORMAL(JsonKeyValues.NORMAL.key, Typeface.NORMAL),
        BOLD(JsonKeyValues.BOLD.key, Typeface.BOLD),
        BOLD_ITALIC(JsonKeyValues.BOLD_ITALIC.key, Typeface.BOLD_ITALIC),
        ITALIC(JsonKeyValues.ITALIC.key, Typeface.ITALIC);

        companion object {

            fun style(key: String?) = values().firstOrNull { it.key == key }?.style ?: NORMAL.style
            fun key(style: Int) = values().firstOrNull { it.style == style }?.key ?: NORMAL.key
        }
    }

    // This class containt mapping between keys used within json of Custom Watchface and preferences
    private enum class PrefMap(val key: String, @StringRes val prefKey: Int, val typeBool: Boolean) {

        SHOW_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_IOB.key, R.string.key_show_iob, true),
        SHOW_DETAILED_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_IOB.key, R.string.key_show_detailed_iob, true),
        SHOW_COB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COB.key, R.string.key_show_cob, true),
        SHOW_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DELTA.key, R.string.key_show_delta, true),
        SHOW_AVG_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AVG_DELTA.key, R.string.key_show_avg_delta, true),
        SHOW_DETAILED_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_DELTA.key, R.string.key_show_detailed_delta, true),
        SHOW_UPLOADER_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY.key, R.string.key_show_uploader_battery, true),
        SHOW_RIG_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_RIG_BATTERY.key, R.string.key_show_rig_battery, true),
        SHOW_TEMP_BASAL(CwfMetadataKey.CWF_PREF_WATCH_SHOW_TEMP_BASAL.key, R.string.key_show_temp_basal, true),
        SHOW_DIRECTION(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DIRECTION.key, R.string.key_show_direction, true),
        SHOW_AGO(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AGO.key, R.string.key_show_ago, true),
        SHOW_BG(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BG.key, R.string.key_show_bg, true),
        SHOW_BGI(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BGI.key, R.string.key_show_bgi, true),
        SHOW_LOOP_STATUS(CwfMetadataKey.CWF_PREF_WATCH_SHOW_LOOP_STATUS.key, R.string.key_show_external_status, true),
        SHOW_WEEK_NUMBER(CwfMetadataKey.CWF_PREF_WATCH_SHOW_WEEK_NUMBER.key, R.string.key_show_week_number, true),
        SHOW_DATE(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DATE.key, R.string.key_show_date, true),
        PREF_UNITS(JsonKeyValues.PREF_UNITS.key, R.string.key_units_mgdl, true),
        PREF_DARK(JsonKeyValues.PREF_DARK.key, R.string.key_dark, true),
        PREF_MATCH_DIVIDER(JsonKeyValues.PREF_MATCH_DIVIDER.key, R.string.key_match_divider, true);

        var value: String = ""

        companion object {

            fun fromKey(key: String) = PrefMap.values().firstOrNull { it.key == key }
        }
    }

    private enum class ValueMap(val key: String, val min: Double, val max: Double) {
        NONE("", 0.0, 0.0),
        SGV(ViewKeys.SGV.key, 39.0, 400.0),
        SGVLEVEL(JsonKeyValues.SGVLEVEL.key, -1.0, 1.0),
        DIRECTION(ViewKeys.DIRECTION.key, 1.0, 7.0),
        DELTA(ViewKeys.DELTA.key, -25.0, 25.0),
        AVG_DELTA(ViewKeys.AVG_DELTA.key, -25.0, 25.0),
        UPLOADER_BATTERY(ViewKeys.UPLOADER_BATTERY.key, 0.0, 100.0),
        RIG_BATTERY(ViewKeys.RIG_BATTERY.key, 0.0, 100.0),
        TIMESTAMP(ViewKeys.TIMESTAMP.key, 0.0, 60.0),
        LOOP(ViewKeys.LOOP.key, 0.0, 28.0),
        DAY(ViewKeys.DAY.key, 1.0, 31.0),
        DAY_NAME(ViewKeys.DAY_NAME.key, 1.0, 7.0),
        MONTH(ViewKeys.MONTH.key, 1.0, 12.0),
        WEEKNUMBER(ViewKeys.WEEKNUMBER.key, 1.0, 53.0);

        fun dynValue(dataValue: Double, dataRange: DataRange, valueRange: DataRange): Int = when {
            dataValue < dataRange.minData -> dataRange.minData
            dataValue > dataRange.maxData -> dataRange.maxData
            else                          -> dataValue
        }.let {
            if (dataRange.minData != dataRange.maxData)
                (valueRange.minData + (it - dataRange.minData) * (valueRange.maxData - valueRange.minData) / (dataRange.maxData - dataRange.minData)).toInt()
            else it.toInt()
        }

        fun stepValue(dataValue: Double, range: DataRange, step: Int): Int = step(dataValue, range, step)
        private fun step(dataValue: Double, dataRange: DataRange, step: Int): Int = when {
            dataValue < dataRange.minData  -> dataRange.minData
            dataValue >= dataRange.maxData -> dataRange.maxData * 0.9999 // to avoid dataValue == maxData and be out of range
            else                           -> dataValue
        }.let { if (dataRange.minData != dataRange.maxData) (1 + ((it - dataRange.minData) * step) / (dataRange.maxData - dataRange.minData)).toInt() else 0 }

        companion object {

            fun fromKey(key: String) = values().firstOrNull { it.key == key } ?: NONE
        }
    }

    private class DynProvider(val cwf: CustomWatchface, val dataJson: JSONObject, val valueMap: ValueMap, val width: Int, val height: Int) {

        private val dynDrawable = mutableMapOf<Int, Drawable?>()
        private val dynColor = mutableMapOf<Int, Int>()
        private val dynFontColor = mutableMapOf<Int, Int>()
        private val dynTextSize = mutableMapOf<Int, Int>()
        private val dynLeftOffset = mutableMapOf<Int, Int>()
        private val dynTopOffset = mutableMapOf<Int, Int>()
        private val dynRotationOffset = mutableMapOf<Int, Int>()
        private var dataRange: DataRange? = null
        private var topRange: DataRange? = null
        private var leftRange: DataRange? = null
        private var rotationRange: DataRange? = null
        val stepDraw: Int
            get() = dynDrawable.size - 1
        val stepColor: Int
            get() = dynColor[0]?.let { dynColor.size - 1 } ?: dynColor.size
        val stepFontColor: Int
            get() = dynFontColor[0]?.let { dynFontColor.size - 1 } ?: dynFontColor.size
        val stepTextSize: Int
            get() = dynTextSize[0]?.let { dynTextSize.size - 1 } ?: dynTextSize.size
        val stepLeftOffset: Int
            get() = dynLeftOffset[0]?.let { dynLeftOffset.size - 1 } ?: dynLeftOffset.size
        val stepTopOffset: Int
            get() = dynTopOffset[0]?.let { dynTopOffset.size - 1 } ?: dynTopOffset.size
        val stepRotationOffset: Int
            get() = dynRotationOffset[0]?.let { dynRotationOffset.size - 1 } ?: dynRotationOffset.size

        val dataValue: Double?
            get() = when (valueMap) {
                ValueMap.NONE             -> 0.0
                ValueMap.SGV              -> if (cwf.singleBg.sgvString != "---") cwf.singleBg.sgv else null
                ValueMap.SGVLEVEL         -> if (cwf.singleBg.sgvString != "---") cwf.singleBg.sgvLevel.toDouble() else null
                ValueMap.DIRECTION        -> TrendArrowMap.value()
                ValueMap.DELTA            -> cwf.singleBg.deltaMgdl
                ValueMap.AVG_DELTA        -> cwf.singleBg.avgDeltaMgdl
                ValueMap.RIG_BATTERY      -> cwf.status.rigBattery.replace("%", "").toDoubleOrNull()
                ValueMap.UPLOADER_BATTERY -> cwf.status.battery.replace("%", "").toDoubleOrNull()
                ValueMap.LOOP             -> if (cwf.status.openApsStatus != -1L) ((System.currentTimeMillis() - cwf.status.openApsStatus) / 1000 / 60).toDouble() else null
                ValueMap.TIMESTAMP        -> if (cwf.singleBg.timeStamp != 0L) floor(cwf.timeSince() / (1000 * 60)) else null
                ValueMap.DAY              -> DateTime().dayOfMonth.toDouble()
                ValueMap.DAY_NAME         -> DateTime().dayOfWeek.toDouble()
                ValueMap.MONTH            -> DateTime().monthOfYear.toDouble()
                ValueMap.WEEKNUMBER       -> DateTime().weekOfWeekyear.toDouble()
            }

        fun getTopOffset(): Int = dataRange?.let { dataRange ->
            topRange?.let { topRange ->
                dataValue?.let { (valueMap.dynValue(it, dataRange, topRange) * cwf.zoomFactor).toInt() }
                    ?: (topRange.invalidData * cwf.zoomFactor).toInt()
            }
        } ?: 0

        fun getLeftOffset(): Int = dataRange?.let { dataRange ->
            leftRange?.let { leftRange ->
                dataValue?.let { (valueMap.dynValue(it, dataRange, leftRange) * cwf.zoomFactor).toInt() }
                    ?: (leftRange.invalidData * cwf.zoomFactor).toInt()
            }
        } ?: 0

        fun getRotationOffset(): Int = dataRange?.let { dataRange -> rotationRange?.let { rotRange -> dataValue?.let { valueMap.dynValue(it, dataRange, rotRange) } ?: rotRange.invalidData } } ?: 0
        fun getDrawable() = dataRange?.let { dataRange -> dataValue?.let { dynDrawable[valueMap.stepValue(it, dataRange, stepDraw)] } ?: dynDrawable[0] }
        fun getFontColorStep() = getIntValue(dynFontColor, stepFontColor)
        fun getTextSizeStep() = getIntValue(dynTextSize, stepTextSize)
        fun getColorStep() = getIntValue(dynColor, stepColor)
        fun getTopOffsetStep() = getIntValue(dynTopOffset, stepTopOffset)
        fun getLeftOffsetStep() = getIntValue(dynLeftOffset, stepLeftOffset)
        fun getRotationOffsetStep() = getIntValue(dynRotationOffset, stepRotationOffset)

        private fun getIntValue(dynMap: MutableMap<Int, Int>, step: Int) =
            if (step > 0) dataRange?.let { dataRange -> dataValue?.let { dynMap[valueMap.stepValue(it, dataRange, step)] } ?: dynMap[0] ?: dynMap[1] } else null

        private fun load() {
            DataRange(dataJson.optDouble(MINDATA.key, valueMap.min), dataJson.optDouble(MAXDATA.key, valueMap.max)).let { defaultRange ->
                dataRange = defaultRange
                topRange = parseDataRange(dataJson.optJSONObject(TOPOFFSET.key), defaultRange)
                leftRange = parseDataRange(dataJson.optJSONObject(LEFTOFFSET.key), defaultRange)
                rotationRange = parseDataRange(dataJson.optJSONObject(ROTATIONOFFSET.key), defaultRange)
            }
            getDrawableSteps(dynDrawable, IMAGE.key, INVALIDIMAGE.key)
            getColorSteps(dynColor, COLOR.key, INVALIDCOLOR.key)
            getColorSteps(dynFontColor, FONTCOLOR.key, INVALIDFONTCOLOR.key)
            getIntSteps(dynTextSize, TEXTSIZE.key, INVALIDTEXTSIZE.key)
            getIntSteps(dynLeftOffset, LEFTOFFSET.key, INVALIDTEXTSIZE.key)
            getIntSteps(dynTopOffset, TOPOFFSET.key, INVALIDTEXTSIZE.key)
            getIntSteps(dynRotationOffset, ROTATIONOFFSET.key, INVALIDTEXTSIZE.key)
        }

        private fun getDrawableSteps(dynMap: MutableMap<Int, Drawable?>, key: String, invalidKey: String) {
            if (dataJson.has(invalidKey))
                dynMap[0] = dataJson.optString(invalidKey)?.let { cwf.resDataMap[it]?.toDrawable(cwf.resources, width, height) }
            var idx = 1
            while (dataJson.has("${key}$idx")) {
                cwf.resDataMap[dataJson.optString("${key}$idx")]?.toDrawable(cwf.resources, width, height).also { dynMap[idx] = it }
                idx++
            }
        }

        private fun getColorSteps(dynMap: MutableMap<Int, Int>, key: String, invalidKey: String) {
            if (dataJson.has(invalidKey))
                dynMap[0] = cwf.getColor(dataJson.optString(invalidKey))
            var idx = 1
            while (dataJson.has("${key}$idx")) {
                dynMap[idx] = cwf.getColor(dataJson.optString("${key}$idx"))
                idx++
            }
        }

        private fun getIntSteps(dynMap: MutableMap<Int, Int>, key: String, invalidKey: String) {
            if (dataJson.has(invalidKey))
                dynMap[0] = dataJson.optInt(invalidKey)
            var idx = 1
            while (dataJson.has("${key}$idx")) {
                dynMap[idx] = dataJson.optInt("${key}$idx", 22)
                idx++
            }
        }

        private fun parseDataRange(json: JSONObject?, defaultData: DataRange) =
            json?.let {
                DataRange(
                    minData = it.optDouble(MINVALUE.key, defaultData.minData),
                    maxData = it.optDouble(MAXVALUE.key, defaultData.maxData),
                    invalidData = it.optInt(INVALIDVALUE.key, defaultData.invalidData)
                )
            } ?: defaultData

        companion object {

            val dynData = mutableMapOf<String, DynProvider>()
            var dynJson: JSONObject? = null
            fun init(cwf: CustomWatchface, json: JSONObject?) {
                cwf.dynPref(json?.optJSONObject((DYNPREF.key)))
                this.dynJson = json?.optJSONObject((DYNDATA.key))
                dynData.clear()
            }

            fun getDyn(cwf: CustomWatchface, keyPref: String, key: String, width: Int, height: Int, defaultViewKey: String): DynProvider? {
                if (dynData[defaultViewKey] != null)
                    return dynData[defaultViewKey]

                cwf.dynPref[keyPref]?.let { dynPref ->
                    ValueMap.fromKey(dynPref.optString(VALUEKEY.key, defaultViewKey)).let { valueMap ->
                        DynProvider(cwf, dynPref, valueMap, width, height).also { it.load() }
                    }
                }?.also { dynData[defaultViewKey] = it }

                if (dynData[defaultViewKey] != null)
                    return dynData[defaultViewKey]

                dynJson?.optJSONObject(key)?.let { dynJson ->
                    ValueMap.fromKey(dynJson.optString(VALUEKEY.key, defaultViewKey)).let { valueMap ->
                        DynProvider(cwf, dynJson, valueMap, width, height).also { it.load() }
                    }
                }?.also { dynData[defaultViewKey] = it }

                return dynData[defaultViewKey]
            }
        }
    }

    private class DataRange(val minData: Double, val maxData: Double, val invalidData: Int = 0)

    // block below build a map of prefKey => json Bloc recursively
    val dynPref = mutableMapOf<String, JSONObject>()
    private val valPref = mutableMapOf<String, String>()
    fun dynPref(dynJson: JSONObject?) {
        valPref.clear()
        dynPref.clear()
        dynJson?.keys()?.forEach { key ->
            val targetJson = JSONObject()
            dynJson.optJSONObject(key)?.let { buildDynPrefs(dynJson, targetJson, it, key, mutableSetOf()) }
        }
    }

    private fun buildDynPrefs(dynJson: JSONObject, targetJson: JSONObject, json: JSONObject, key: String, visitedKeys: MutableSet<String>) {
        val prefKey = json.optString(PREFKEY.key)
        PrefMap.fromKey(prefKey)?.let { prefMap ->
            val value = valPref[prefMap.key]
                ?: (if (prefMap.typeBool) sp.getBoolean(prefMap.prefKey, false).toString() else sp.getString(prefMap.prefKey, "")).also {
                    valPref[prefMap.key] = it
                }
            json.optJSONObject(value)?.let { nextJson ->
                if (nextJson.has(DYNPREF.key)) {
                    nextJson.keys().forEach { key ->
                        if (key != DYNPREF.key)
                            targetJson.putOpt(key, nextJson.opt(key))
                    }
                    val nextKey = nextJson.optString(DYNPREF.key)
                    if (nextKey.isNotEmpty() && nextKey !in visitedKeys) {
                        visitedKeys += nextKey
                        dynJson.optJSONObject(nextKey)?.let {
                            buildDynPrefs(dynJson, targetJson, it, key, visitedKeys)
                        }
                    }
                } else {
                    nextJson.keys().forEach { key ->
                        if (key != DYNPREF.key)
                            targetJson.putOpt(key, nextJson.opt(key))
                    }
                    dynPref[key] = targetJson
                }
            }
        }
    }

    private fun checkPref() = valPref.any { (prefMap, s) ->
        s != PrefMap.fromKey(prefMap)?.let { if (it.typeBool) sp.getBoolean(it.prefKey, false).toString() else sp.getString(it.prefKey, "") }
    }
}


