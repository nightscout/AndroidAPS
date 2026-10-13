package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
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
import androidx.core.graphics.toColorInt
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventUpdateSelectedWatchface
import app.aaps.core.interfaces.rx.weardata.CUSTOM_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.CwfMetadataMap
import app.aaps.core.interfaces.rx.weardata.CwfResDataMap
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.ResData
import app.aaps.core.interfaces.rx.weardata.ResFormat
import app.aaps.core.interfaces.rx.weardata.isEquals
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.shared.impl.weardata.JsonKeyValues
import app.aaps.shared.impl.weardata.JsonKeys
import app.aaps.shared.impl.weardata.ResFileMap
import app.aaps.shared.impl.weardata.ViewKeys
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import app.aaps.shared.impl.weardata.toDrawable
import app.aaps.shared.impl.weardata.toTypeface
import app.aaps.wear.R
import app.aaps.wear.databinding.ActivityCustomBinding
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlin.math.floor

@SuppressLint("Deprecated")
class CustomWatchface : BaseWatchFace() {

    @Inject lateinit var context: Context
    private lateinit var binding: ActivityCustomBinding
    private var zoomFactor = 1.0
    private val templeResolution = 400
    private var lowBatColor = Color.RED
    private var resDataMap: CwfResDataMap = mutableMapOf()
    private var json = JSONObject()
    private var jsonString = ""

    private fun bgColor(dataSet: Int): Int = when (singleBg[dataSet].sgvLevel) {
        1L   -> highColor
        0L   -> midColor
        -1L  -> lowColor
        else -> midColor
    }

    private fun tempTargetColor(dataSet: Int): Int = when (status[dataSet].tempTargetLevel) {
        0   -> tempTargetProfileColor
        1   -> tempTargetLoopColor
        2  -> tempTargetColor
        else -> tempTargetProfileColor
    }

    private fun reservoirColor(dataSet: Int): Int = when (status[dataSet].reservoirLevel) {
        0    -> reservoirColor
        1    -> reservoirWarningColor
        2    -> reservoirUrgentColor
        else -> reservoirColor
    }

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        sp.putInt(R.string.key_last_selected_watchface, SelectedWatchFace.CUSTOM.ordinal)
        rxBus.send(EventUpdateSelectedWatchface())
        binding = ActivityCustomBinding.inflate(inflater)
        setDefaultColors()
        runBlocking {
            complicationDataRepository.storeCustomWatchface(
                customWatchface = defaultWatchface(false).customWatchfaceData,
                customWatchfaceFull = defaultWatchface(true).customWatchfaceData,
                isDefault = true
            )
        }
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val displayWidth = windowManager.currentWindowMetrics.bounds.width()
        zoomFactor = displayWidth.toDouble() / templeResolution.toDouble()
        return binding
    }

    // Always update at 1 second intervals for smooth analog clock hand movement
    override fun getInteractiveModeUpdateRate(): Long = 1000L

    override fun onTimeChanged(oldTime: app.aaps.wear.watchfaces.utils.WatchFaceTime, newTime: app.aaps.wear.watchfaces.utils.WatchFaceTime) {
        super.onTimeChanged(oldTime, newTime)
        // Update analog clock hands every second for smooth movement
        if (::binding.isInitialized && newTime.hasSecondChanged(oldTime)) {
            updateClockHands()
        }
    }

    private fun updateClockHands() {
        val now = LocalTime.now()
        binding.secondHand.rotation = now.second * 6f
        binding.minuteHand.rotation = now.minute * 6f + now.second * 0.1f
        binding.hourHand.rotation = now.hour * 30f + now.minute * 0.5f + now.second * (0.5f / 60f)
    }

    override fun setDataFields() {
        super.setDataFields()
        binding.direction.setImageDrawable(TrendArrowMap.drawable())
        binding.directionExt1.setImageDrawable(TrendArrowMap.drawableExt1())
        binding.directionExt2.setImageDrawable(TrendArrowMap.drawableExt2())
        // Update analog clock hands
        updateClockHands()
    }

    override fun setColorDark() {
        setWatchfaceStyle()
        if ((ViewMap.TEMP_TARGET.dynData?.stepFontColor ?: 0) <= 0)
            binding.tempTarget.setTextColor(tempTargetColor(0))
        if ((ViewMap.RESERVOIR.dynData?.stepFontColor ?: 0) <= 0)
            binding.reservoir.setTextColor(reservoirColor(0))
        if ((ViewMap.SGV.dynData?.stepFontColor ?: 0) <= 0)
            binding.sgv.setTextColor(bgColor(0))
        if ((ViewMap.DIRECTION.dynData?.stepColor ?: 0) <= 0)
            binding.direction.colorFilter = changeDrawableColor(bgColor(0))
        if (ageLevel() != 1 && (ViewMap.TIMESTAMP.dynData?.stepFontColor ?: 0) <= 0)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if (status[0].batteryLevel != 1 && (ViewMap.UPLOADER_BATTERY.dynData?.stepFontColor ?: 0) <= 0)
            binding.uploaderBattery.setTextColor(lowBatColor)
        if ((ViewMap.LOOP.dynData?.stepDraw ?: 0) <= 0)     // Apply automatic background image only if no dynData or no step images
            when (loopLevel) {
                -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
                else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
            }
        //Management of External data 1
        if ((ViewMap.TEMP_TARGET_EXT1.dynData?.stepFontColor ?: 0) <= 0)
            binding.tempTargetExt1.setTextColor(tempTargetColor(1))
        if ((ViewMap.RESERVOIR_EXT1.dynData?.stepFontColor ?: 0) <= 0)
            binding.reservoirExt1.setTextColor(reservoirColor(1))
        if ((ViewMap.SGV_EXT1.dynData?.stepFontColor ?: 0) <= 0)
            binding.sgvExt1.setTextColor(bgColor(1))
        if ((ViewMap.DIRECTION_EXT1.dynData?.stepColor ?: 0) <= 0)
            binding.directionExt1.colorFilter = changeDrawableColor(bgColor(1))
        if (ageLevel(id = 1) != 1 && (ViewMap.TIMESTAMP_EXT1.dynData?.stepFontColor ?: 0) <= 0)
            binding.timestampExt1.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if ((ViewMap.LOOP_EXT1.dynData?.stepDraw ?: 0) <= 0)     // Apply automatic background image only if no dynData or no step images
            when (loopLevelExt1) {
                -1   -> binding.loopExt1.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> binding.loopExt1.setBackgroundResource(R.drawable.loop_green_25)
                else -> binding.loopExt1.setBackgroundResource(R.drawable.loop_red_25)
            }
        //Management of External data 2
        if ((ViewMap.TEMP_TARGET_EXT1.dynData?.stepFontColor ?: 0) <= 0)
            binding.tempTargetExt2.setTextColor(tempTargetColor(2))
        if ((ViewMap.RESERVOIR_EXT2.dynData?.stepFontColor ?: 0) <= 0)
            binding.reservoirExt2.setTextColor(reservoirColor(2))
        if ((ViewMap.SGV_EXT2.dynData?.stepFontColor ?: 0) <= 0)
            binding.sgvExt2.setTextColor(bgColor(2))
        if ((ViewMap.DIRECTION_EXT2.dynData?.stepColor ?: 0) <= 0)
            binding.directionExt2.colorFilter = changeDrawableColor(bgColor(2))
        if (ageLevel(id = 2) != 1 && (ViewMap.TIMESTAMP_EXT2.dynData?.stepFontColor ?: 0) <= 0)
            binding.timestampExt2.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if ((ViewMap.LOOP_EXT2.dynData?.stepDraw ?: 0) <= 0)     // Apply automatic background image only if no dynData or no step images
            when (loopLevelExt2) {
                -1   -> binding.loopExt2.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> binding.loopExt2.setBackgroundResource(R.drawable.loop_green_25)
                else -> binding.loopExt2.setBackgroundResource(R.drawable.loop_red_25)
            }
        //******************************
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
        // Update analog clock hands for smooth movement
        updateClockHands()
    }

    override fun updateSecondVisibility() {
        binding.second.visibility = (binding.second.isVisible && showSecond).toVisibility()
        binding.secondHand.visibility = (binding.secondHand.isVisible && showSecond).toVisibility()
    }

    private fun setWatchfaceStyle() {
        var customWatchfaceData = runBlocking {
            complicationDataRepository.getCustomWatchface() ?: complicationDataRepository.getCustomWatchface(true)
        }
        if (customWatchfaceData == null) { // if neither CWF or Default CWF is found, then force reload of default Layout
            super.onCreate()
            customWatchfaceData = runBlocking { complicationDataRepository.getCustomWatchface(true) }
        }
        customWatchfaceData?.let {
            updatePref(it.metadata)
            try {
                json = JSONObject(it.json)
                if (!resDataMap.isEquals(it.resData) || jsonString != it.json) {
                    resDataMap = it.resData
                    jsonString = it.json
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

                enableSecond = json.optBoolean(JsonKeys.ENABLESECOND.key) && sp.getBoolean(PrefMap.SHOW_SECOND.prefKey, PrefMap.SHOW_SECOND.defaultValue as Boolean)
                pointSize = json.optInt(JsonKeys.POINTSIZE.key, 2)
                dayNameFormat = json.optString(JsonKeys.DAYNAMEFORMAT.key, "E").takeIf { it.matches(Regex("E{1,4}")) } ?: "E"
                monthFormat = json.optString(JsonKeys.MONTHFORMAT.key, "MMM").takeIf { it.matches(Regex("M{1,4}")) } ?: "MMM"
                binding.dayName.text = dateUtil.dayNameString(dayNameFormat).substringBeforeLast(".") // Update dayName and month according to format on cwf loading
                binding.month.text = dateUtil.monthString(monthFormat).substringBeforeLast(".")
                val jsonColor = dynPref[json.optString(JsonKeys.DYNPREFCOLOR.key)] ?: json
                highColor = getColor(jsonColor.optString(JsonKeys.HIGHCOLOR.key), ContextCompat.getColor(this, R.color.dark_highColor))
                midColor = getColor(jsonColor.optString(JsonKeys.MIDCOLOR.key), ContextCompat.getColor(this, R.color.inrange))
                lowColor = getColor(jsonColor.optString(JsonKeys.LOWCOLOR.key), ContextCompat.getColor(this, R.color.low))
                lowBatColor = getColor(jsonColor.optString(JsonKeys.LOWBATCOLOR.key), ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))
                carbColor = getColor(jsonColor.optString(JsonKeys.CARBCOLOR.key), ContextCompat.getColor(this, R.color.carbs))
                basalBackgroundColor = getColor(jsonColor.optString(JsonKeys.BASALBACKGROUNDCOLOR.key), ContextCompat.getColor(this, R.color.basal_dark))
                basalCenterColor = getColor(jsonColor.optString(JsonKeys.BASALCENTERCOLOR.key), ContextCompat.getColor(this, R.color.basal_light))
                gridColor = getColor(jsonColor.optString(JsonKeys.GRIDCOLOR.key), Color.WHITE)
                tempTargetProfileColor = getColor(jsonColor.optString(JsonKeys.TEMPTARGETPROFILECOLOR.key), Color.WHITE)
                tempTargetLoopColor = getColor(jsonColor.optString(JsonKeys.TEMPTARGETLOOPCOLOR.key), ContextCompat.getColor(this, R.color.dark_tempTarget_Loop))
                tempTargetColor = getColor(jsonColor.optString(JsonKeys.TEMPTARGETCOLOR.key), ContextCompat.getColor(this, R.color.dark_tempTarget))
                reservoirColor = getColor(jsonColor.optString(JsonKeys.RESERVOIRCOLOR.key), Color.WHITE)
                reservoirWarningColor = getColor(jsonColor.optString(JsonKeys.RESERVOIRWARNINGCOLOR.key), ContextCompat.getColor(this, R.color.dark_warning))
                reservoirUrgentColor = getColor(jsonColor.optString(JsonKeys.RESERVOIRURGENTCOLOR.key), ContextCompat.getColor(this, R.color.dark_alarm))
                enableExt1 = false
                enableExt2 = false
                binding.mainLayout.forEach { view ->
                    ViewMap.fromId(view.id)?.let { viewMap ->
                        when (view) {
                            is TextView                                 -> viewMap.customizeTextView(view)
                            is ImageView                                -> viewMap.customizeImageView(view)
                            is lecho.lib.hellocharts.view.LineChartView -> viewMap.customizeGraphView(view)
                            else                                        -> viewMap.customizeViewCommon(view)
                        }
                        if (viewMap.external == 1 && view.isVisible)
                            enableExt1 = true

                        if (viewMap.external == 2 && view.isVisible)
                            enableExt2 = true
                    }
                }
                manageSpecificViews()
            } catch (_: Exception) {
                aapsLogger.debug(LTag.WEAR, "Crash during Custom watch load")
                runBlocking {
                    complicationDataRepository.storeCustomWatchface(defaultWatchface(true).customWatchfaceData, isDefault = false)
                } // reload correct values to avoid crash of watchface
            }
        }
    }

    private fun updatePref(metadata: CwfMetadataMap) {
        val cwfAuthorization = metadata[CwfMetadataKey.CWF_AUTHORIZATION]?.toBooleanStrictOrNull()
        cwfAuthorization?.let { authorization ->
            if (authorization) {
                PrefMap.entries.forEach { pref ->
                    metadata[CwfMetadataKey.fromKey(pref.key)]?.toBooleanStrictOrNull()?.let { sp.putBoolean(pref.prefKey, it) }
                }
            }
        }
    }

    private fun defaultWatchface(externalViews: Boolean): EventData.ActionSetCustomWatchface {
        val metadata = JSONObject()
            .put(CwfMetadataKey.CWF_NAME.key, getString(app.aaps.core.interfaces.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_FILENAME.key, getString(app.aaps.core.interfaces.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_AUTHOR.key, "Philoul")
            .put(CwfMetadataKey.CWF_CREATED_AT.key, dateUtil.dateString(dateUtil.now()))
            .put(CwfMetadataKey.CWF_AUTHOR_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_COMMENT.key, if (externalViews) getString(app.aaps.core.interfaces.R.string.default_custom_watchface_external_comment) else getString(app.aaps.core.interfaces.R.string.default_custom_watchface_comment))
        val json = JSONObject()
            .put(JsonKeys.METADATA.key, metadata)
            .put(JsonKeys.HIGHCOLOR.key, String.format("#%06X", 0xFFFFFF and highColor))
            .put(JsonKeys.MIDCOLOR.key, String.format("#%06X", 0xFFFFFF and midColor))
            .put(JsonKeys.LOWCOLOR.key, String.format("#%06X", 0xFFFFFF and lowColor))
            .put(JsonKeys.LOWBATCOLOR.key, String.format("#%06X", 0xFFFFFF and lowBatColor))
            .put(JsonKeys.CARBCOLOR.key, String.format("#%06X", 0xFFFFFF and carbColor))
            .put(JsonKeys.BASALBACKGROUNDCOLOR.key, String.format("#%06X", 0xFFFFFF and basalBackgroundColor))
            .put(JsonKeys.BASALCENTERCOLOR.key, String.format("#%06X", 0xFFFFFF and basalCenterColor))
            .put(JsonKeys.GRIDCOLOR.key, String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put(JsonKeys.TEMPTARGETPROFILECOLOR.key, String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put(JsonKeys.TEMPTARGETLOOPCOLOR.key, String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(this, R.color.dark_tempTarget_Loop)))
            .put(JsonKeys.TEMPTARGETCOLOR.key, String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(this, R.color.dark_tempTarget)))
            .put(JsonKeys.RESERVOIRCOLOR.key, String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put(JsonKeys.RESERVOIRWARNINGCOLOR.key, String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(this, R.color.dark_warning)))
            .put(JsonKeys.RESERVOIRURGENTCOLOR.key, String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(this, R.color.dark_alarm)))
            .put(JsonKeys.POINTSIZE.key, 2)
            .put(JsonKeys.ENABLESECOND.key, true)

        binding.mainLayout.forEach { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            ViewMap.fromId(view.id)?.let {
                if (it.external == 0 || externalViews) {
                    if (view is TextView) {
                        json.put(
                            it.key,
                            JSONObject()
                                .put(JsonKeys.WIDTH.key, (params.width / zoomFactor).toInt())
                                .put(JsonKeys.HEIGHT.key, (params.height / zoomFactor).toInt())
                                .put(JsonKeys.TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                                .put(JsonKeys.LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                                .put(JsonKeys.ROTATION.key, view.rotation.toInt())
                                .put(JsonKeys.VISIBILITY.key, getVisibility(view.visibility))
                                .put(JsonKeys.TEXTSIZE.key, view.textSize.toInt())
                                .put(JsonKeys.GRAVITY.key, GravityMap.key(view.gravity))
                                .put(JsonKeys.FONT.key, FontMap.key())
                                .put(JsonKeys.FONTSTYLE.key, StyleMap.key(view.typeface.style))
                                .put(JsonKeys.FONTCOLOR.key, String.format("#%06X", 0xFFFFFF and view.currentTextColor))
                        )
                    }
                    if (view is ImageView || view is lecho.lib.hellocharts.view.LineChartView) {
                        json.put(
                            it.key,
                            JSONObject()
                                .put(JsonKeys.WIDTH.key, (params.width / zoomFactor).toInt())
                                .put(JsonKeys.HEIGHT.key, (params.height / zoomFactor).toInt())
                                .put(JsonKeys.TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                                .put(JsonKeys.LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                                .put(JsonKeys.VISIBILITY.key, getVisibility(view.visibility))
                        )
                    }
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
        highColor = "#FFFF00".toColorInt()
        midColor = "#00FF00".toColorInt()
        lowColor = "#FF0000".toColorInt()
        carbColor = ContextCompat.getColor(this, R.color.carbs)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        lowBatColor = ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
        tempTargetProfileColor =  Color.WHITE
        tempTargetLoopColor =  ContextCompat.getColor(this, R.color.dark_tempTarget_Loop)
        tempTargetColor =  ContextCompat.getColor(this, R.color.dark_tempTarget)
        reservoirColor = Color.WHITE
        reservoirWarningColor = ContextCompat.getColor(this, R.color.dark_warning)
        reservoirUrgentColor = ContextCompat.getColor(this, R.color.dark_alarm)
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
        when (color) {
            JsonKeyValues.BGCOLOR.key      -> bgColor(0)
            JsonKeyValues.BGCOLOR_EXT1.key -> bgColor(1)
            JsonKeyValues.BGCOLOR_EXT2.key -> bgColor(2)
            else                           ->
                try {
                    color.toColorInt()
                } catch (_: Exception) {
                    defaultColor
                }
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
        binding.timePeriod.visibility = (binding.timePeriod.isVisible && DateFormat.is24HourFormat(this).not()).toVisibility()
    }

    private enum class ViewMap(
        val key: String,
        @IdRes val id: Int,
        val pref: PrefMap? = null,                  // PreMap key should be typeBool == true to manage visibility of associated View
        @IdRes val defaultDrawable: Int? = null,
        val customDrawable: ResFileMap? = null,
        val customHigh: ResFileMap? = null,
        val customLow: ResFileMap? = null,
        val external: Int = 0
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
        PATIENT_NAME_EXT1(ViewKeys.PATIENT_NAME_EXT1.key, R.id.patient_name_ext1, external = 1),
        IOB1_EXT1(ViewKeys.IOB1_EXT1.key, R.id.iob1_ext1, PrefMap.SHOW_IOB, external = 1),
        IOB2_EXT1(ViewKeys.IOB2_EXT1.key, R.id.iob2_ext1, PrefMap.SHOW_IOB, external = 1),
        COB1_EXT1(ViewKeys.COB1_EXT1.key, R.id.cob1_ext1,PrefMap.SHOW_COB, external = 1),
        COB2_EXT1(ViewKeys.COB2_EXT1.key, R.id.cob2_ext1,PrefMap.SHOW_COB, external = 1),
        DELTA_EXT1(ViewKeys.DELTA_EXT1.key, R.id.delta_ext1,PrefMap.SHOW_DELTA, external = 1),
        AVG_DELTA_EXT1(ViewKeys.AVG_DELTA_EXT1.key, R.id.avg_delta_ext1, PrefMap.SHOW_AVG_DELTA, external = 1),
        TEMP_TARGET_EXT1(ViewKeys.TEMP_TARGET_EXT1.key, R.id.temp_target_ext1, PrefMap.SHOW_TEMP_TARGET, external = 1),
        RESERVOIR_EXT1(ViewKeys.RESERVOIR_EXT1.key, R.id.reservoir_ext1, PrefMap.SHOW_RESERVOIR_LEVEL, external = 1),
        RIG_BATTERY_EXT1(ViewKeys.RIG_BATTERY_EXT1.key, R.id.rig_battery_ext1, PrefMap.SHOW_RIG_BATTERY, external = 1),
        BASALRATE_EXT1(ViewKeys.BASALRATE_EXT1.key, R.id.basalRate_ext1, PrefMap.SHOW_TEMP_BASAL, external = 1),
        BGI_EXT1(ViewKeys.BGI_EXT1.key, R.id.bgi_ext1, PrefMap.SHOW_BGI, external = 1),
        STATUS_EXT1(ViewKeys.STATUS_EXT1.key, R.id.status_ext1, PrefMap.SHOW_LOOP_STATUS, external = 1),
        LOOP_EXT1(ViewKeys.LOOP_EXT1.key, R.id.loop_ext1, PrefMap.SHOW_LOOP_STATUS, external = 1),
        DIRECTION_EXT1(ViewKeys.DIRECTION_EXT1.key, R.id.direction_ext1, PrefMap.SHOW_DIRECTION, external = 1),
        TIMESTAMP_EXT1(ViewKeys.TIMESTAMP_EXT1.key, R.id.timestamp_ext1, PrefMap.SHOW_AGO, external = 1),
        SGV_EXT1(ViewKeys.SGV_EXT1.key, R.id.sgv_ext1, PrefMap.SHOW_BG, external = 1),
        PATIENT_NAME_EXT2(ViewKeys.PATIENT_NAME_EXT2.key, R.id.patient_name_ext2, external = 2),
        IOB1_EXT2(ViewKeys.IOB1_EXT2.key, R.id.iob1_ext2, PrefMap.SHOW_IOB, external = 2),
        IOB2_EXT2(ViewKeys.IOB2_EXT2.key, R.id.iob2_ext2, PrefMap.SHOW_IOB, external = 2),
        COB1_EXT2(ViewKeys.COB1_EXT2.key, R.id.cob1_ext2, PrefMap.SHOW_COB, external = 2),
        COB2_EXT2(ViewKeys.COB2_EXT2.key, R.id.cob2_ext2, PrefMap.SHOW_COB, external = 2),
        DELTA_EXT2(ViewKeys.DELTA_EXT2.key, R.id.delta_ext2, PrefMap.SHOW_DELTA, external = 2),
        AVG_DELTA_EXT2(ViewKeys.AVG_DELTA_EXT2.key, R.id.avg_delta_ext2, PrefMap.SHOW_AVG_DELTA, external = 2),
        TEMP_TARGET_EXT2(ViewKeys.TEMP_TARGET_EXT2.key, R.id.temp_target_ext2, PrefMap.SHOW_TEMP_TARGET, external = 2),
        RESERVOIR_EXT2(ViewKeys.RESERVOIR_EXT2.key, R.id.reservoir_ext2, PrefMap.SHOW_RESERVOIR_LEVEL, external = 2),
        RIG_BATTERY_EXT2(ViewKeys.RIG_BATTERY_EXT2.key, R.id.rig_battery_ext2, PrefMap.SHOW_RIG_BATTERY, external = 2),
        BASALRATE_EXT2(ViewKeys.BASALRATE_EXT2.key, R.id.basalRate_ext2, PrefMap.SHOW_TEMP_BASAL, external = 2),
        BGI_EXT2(ViewKeys.BGI_EXT2.key, R.id.bgi_ext2, PrefMap.SHOW_BGI, external = 2),
        STATUS_EXT2(ViewKeys.STATUS_EXT2.key, R.id.status_ext2, PrefMap.SHOW_LOOP_STATUS, external = 2),
        LOOP_EXT2(ViewKeys.LOOP_EXT2.key, R.id.loop_ext2, PrefMap.SHOW_LOOP_STATUS, external = 2),
        DIRECTION_EXT2(ViewKeys.DIRECTION_EXT2.key, R.id.direction_ext2, PrefMap.SHOW_DIRECTION, external = 2),
        TIMESTAMP_EXT2(ViewKeys.TIMESTAMP_EXT2.key, R.id.timestamp_ext2, PrefMap.SHOW_AGO, external = 2),
        SGV_EXT2(ViewKeys.SGV_EXT2.key, R.id.sgv_ext2, PrefMap.SHOW_BG, external = 2),
        PATIENT_NAME(ViewKeys.PATIENT_NAME.key, R.id.patient_name),
        IOB1(ViewKeys.IOB1.key, R.id.iob1, PrefMap.SHOW_IOB),
        IOB2(ViewKeys.IOB2.key, R.id.iob2, PrefMap.SHOW_IOB),
        COB1(ViewKeys.COB1.key, R.id.cob1, PrefMap.SHOW_COB),
        COB2(ViewKeys.COB2.key, R.id.cob2, PrefMap.SHOW_COB),
        DELTA(ViewKeys.DELTA.key, R.id.delta, PrefMap.SHOW_DELTA),
        AVG_DELTA(ViewKeys.AVG_DELTA.key, R.id.avg_delta, PrefMap.SHOW_AVG_DELTA),
        TEMP_TARGET(ViewKeys.TEMP_TARGET.key, R.id.temp_target, PrefMap.SHOW_TEMP_TARGET),
        RESERVOIR(ViewKeys.RESERVOIR.key, R.id.reservoir, PrefMap.SHOW_RESERVOIR_LEVEL),
        UPLOADER_BATTERY(ViewKeys.UPLOADER_BATTERY.key, R.id.uploader_battery, PrefMap.SHOW_UPLOADER_BATTERY),
        RIG_BATTERY(ViewKeys.RIG_BATTERY.key, R.id.rig_battery, PrefMap.SHOW_RIG_BATTERY),
        BASALRATE(ViewKeys.BASALRATE.key, R.id.basalRate, PrefMap.SHOW_TEMP_BASAL),
        BGI(ViewKeys.BGI.key, R.id.bgi, PrefMap.SHOW_BGI),
        STATUS(ViewKeys.STATUS.key, R.id.status, PrefMap.SHOW_LOOP_STATUS),
        TIME(ViewKeys.TIME.key, R.id.time),
        HOUR(ViewKeys.HOUR.key, R.id.hour),
        MINUTE(ViewKeys.MINUTE.key, R.id.minute),
        SECOND(ViewKeys.SECOND.key, R.id.second, PrefMap.SHOW_SECOND),
        TIMEPERIOD(ViewKeys.TIMEPERIOD.key, R.id.timePeriod),
        DAY_NAME(ViewKeys.DAY_NAME.key, R.id.day_name, PrefMap.SHOW_DATE),
        DAY(ViewKeys.DAY.key, R.id.day, PrefMap.SHOW_DATE),
        WEEK_NUMBER(ViewKeys.WEEK_NUMBER.key, R.id.week_number, PrefMap.SHOW_WEEK_NUMBER),
        MONTH(ViewKeys.MONTH.key, R.id.month, PrefMap.SHOW_DATE),
        LOOP(ViewKeys.LOOP.key, R.id.loop, PrefMap.SHOW_LOOP_STATUS),
        DIRECTION(ViewKeys.DIRECTION.key, R.id.direction, PrefMap.SHOW_DIRECTION),
        TIMESTAMP(ViewKeys.TIMESTAMP.key, R.id.timestamp, PrefMap.SHOW_AGO),
        SGV(ViewKeys.SGV.key, R.id.sgv, PrefMap.SHOW_BG),
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
            pref = PrefMap.SHOW_SECOND,
            defaultDrawable = R.drawable.second_hand,
            customDrawable = ResFileMap.SECOND_HAND,
            customHigh = ResFileMap.SECOND_HAND_HIGH,
            customLow = ResFileMap.SECOND_HAND_LOW
        );

        companion object {

            const val TRANSPARENT = "#00000000"
            fun init(cwf: CustomWatchface) = entries.forEach {
                it.cwf = cwf
                // reset all customized drawable when new watchface is loaded
                it.rangeCustom = null
                it.highCustom = null
                it.lowCustom = null
                it.textDrawable = null
                it.viewJson = null
                it.twinView = null
            }

            fun fromId(id: Int): ViewMap? = entries.firstOrNull { it.id == id }
            fun fromKey(key: String?): ViewMap? = entries.firstOrNull { it.key == key }
        }

        lateinit var cwf: CustomWatchface
        var width = 0
        var height = 0
        var left = 0
        var top = 0
        var viewJson: JSONObject? = null
            get() = field ?: cwf.json.optJSONObject(key)?.also { viewJson = it }
        val visibility: Int
            get() = viewJson?.let { cwf.setVisibility(it.optString(JsonKeys.VISIBILITY.key, JsonKeyValues.GONE.key), visibility()) } ?: View.GONE
        var dynData: DynProvider? = null
        var rangeCustom: Drawable? = null
            get() = field ?: customDrawable?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { rangeCustom = it } }
        var highCustom: Drawable? = null
            get() = field ?: customHigh?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { highCustom = it } }
        var lowCustom: Drawable? = null
            get() = field ?: customLow?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources).also { lowCustom = it } }
        var textDrawable: Drawable? = null
        val drawable: Drawable?
            get() = dynData?.getDrawable() ?: when (cwf.singleBg[0].sgvLevel) {
                1L   -> highCustom ?: rangeCustom
                0L   -> rangeCustom
                -1L  -> lowCustom ?: rangeCustom
                else -> rangeCustom
            }
        var twinView: ViewMap? = null
            get() = field ?: viewJson?.let { viewJson -> ViewMap.fromKey(viewJson.optString(JsonKeys.TWINVIEW.key)).also { twinView = it } }

        fun visibility(): Boolean = this.pref?.let { cwf.sp.getBoolean(it.prefKey, it.defaultValue as Boolean) } != false

        fun textDrawable(): Drawable? = textDrawable
            ?: cwf.resDataMap[viewJson?.optString(JsonKeys.BACKGROUND.key)]?.toDrawable(cwf.resources, width, height)?.also { textDrawable = it }

        fun customizeViewCommon(view: View) {
            view.visibility = visibility
            viewJson?.let { viewJson ->
                width = (viewJson.optInt(JsonKeys.WIDTH.key) * cwf.zoomFactor).toInt()
                height = (viewJson.optInt(JsonKeys.HEIGHT.key) * cwf.zoomFactor).toInt()
                left = (viewJson.optInt(JsonKeys.LEFTMARGIN.key) * cwf.zoomFactor).toInt()
                top = (viewJson.optInt(JsonKeys.TOPMARGIN.key) * cwf.zoomFactor).toInt()
                val params = FrameLayout.LayoutParams(width, height)
                dynData = DynProvider.getDyn(cwf, viewJson.optString(JsonKeys.DYNPREF.key), viewJson.optString(JsonKeys.DYNDATA.key), width, height, key)
                val topOffset = ((if (viewJson.optBoolean(JsonKeys.TOPOFFSET.key, false)) dynData?.getTopOffset() ?: 0 else 0) * cwf.zoomFactor).toInt()
                val topOffsetTwin = ((twinView?.let { if (it.visibility != View.VISIBLE) viewJson.optInt(JsonKeys.TOPOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val topOffsetStep = ((dynData?.getTopOffsetStep() ?: 0) * cwf.zoomFactor).toInt()
                params.topMargin = top + topOffset + topOffsetTwin + topOffsetStep
                val leftOffset = ((if (viewJson.optBoolean(JsonKeys.LEFTOFFSET.key, false)) dynData?.getLeftOffset() ?: 0 else 0) * cwf.zoomFactor).toInt()
                val leftOffsetTwin = ((twinView?.let { if (it.visibility != View.VISIBLE) viewJson.optInt(JsonKeys.LEFTOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val leftOffsetStep = ((dynData?.getLeftOffsetStep() ?: 0) * cwf.zoomFactor).toInt()
                params.leftMargin = left + leftOffset + leftOffsetTwin + leftOffsetStep
                view.layoutParams = params
                val rotationOffset = if (viewJson.optBoolean(JsonKeys.ROTATIONOFFSET.key, false)) dynData?.getRotationOffset()?.toFloat() ?: 0F else 0F
                val rotationOffsetStep = (dynData?.getRotationOffsetStep() ?: 0).toFloat()
                view.rotation = viewJson.optInt(JsonKeys.ROTATION.key).toFloat() + rotationOffset + rotationOffsetStep
            }
        }

        fun customizeTextView(view: TextView) {
            customizeViewCommon(view)
            viewJson?.let { viewJson ->
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ((dynData?.getTextSizeStep() ?: viewJson.optInt(JsonKeys.TEXTSIZE.key, 22)) * cwf.zoomFactor).toFloat())
                view.gravity = GravityMap.gravity(viewJson.optString(JsonKeys.GRAVITY.key, GravityMap.CENTER.key))
                view.setTypeface(
                    FontMap.font(viewJson.optString(JsonKeys.FONT.key, FontMap.DEFAULT.key)),
                    StyleMap.style(viewJson.optString(JsonKeys.FONTSTYLE.key, StyleMap.NORMAL.key))
                )
                view.setTextColor(dynData?.getFontColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.FONTCOLOR.key)))
                view.isAllCaps = viewJson.optBoolean(JsonKeys.ALLCAPS.key)
                if (viewJson.has(JsonKeys.TEXTVALUE.key) || viewJson.has(JsonKeys.DYNVALUE.key) || (dynData?.stepTextValue ?: 0) > 0) {
                    if (viewJson.has(JsonKeys.DYNVALUE.key)) {
                        dynData?.getDynValue(viewJson.optBoolean(JsonKeys.DYNVALUE.key, false))?.let {
                            try {   // try - catch block if wrong format provided not consistant with double values, to avoid crash
                                view.text = String.format(cwf.context.resources.configuration.locales[0], dynData?.getTextValueStep() ?: viewJson.optString(JsonKeys.TEXTVALUE.key, "%.0f"), it)
                            } catch (_: Exception) {
                                view.text = String.format(cwf.context.resources.configuration.locales[0], "%.0f", it)
                            }
                        } ?: apply {
                            view.text = dynData?.getTextValueStep() ?: viewJson.optString(JsonKeys.TEXTVALUE.key)
                        }
                    } else {
                        view.text = dynData?.getTextValueStep() ?: viewJson.optString(JsonKeys.TEXTVALUE.key)
                    }
                }
                (dynData?.getDrawable() ?: textDrawable())?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)           // Note only works on bitmap (png or jpg)  not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                    // if no drawable loaded either background key or dynData, then apply color to text background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
                }
            } ?: apply { view.text = "" }
        }

        fun customizeImageView(view: ImageView) {
            customizeViewCommon(view)
            view.clearColorFilter()
            viewJson?.let { viewJson ->
                drawable?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.setImageDrawable(it)
                } ?: apply {
                    view.setImageDrawable(defaultDrawable?.let { ResourcesCompat.getDrawable(cwf.resources, it, cwf.theme) })
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)       // works on xml included into res files
                        view.setColorFilter(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        view.clearColorFilter()
                }
                if (view.drawable == null)                                              // if no drowable (either default, hardcoded or dynData, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
            }
        }

        fun customizeGraphView(view: lecho.lib.hellocharts.view.LineChartView) {
            customizeViewCommon(view)
            viewJson?.let { viewJson ->
                (dynData?.getDrawable() ?: textDrawable())?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                // if no drowable loaded, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep() ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
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

            fun init(cwf: CustomWatchface) = entries.forEach {
                it.cwf = cwf
                it.arrowCustom = null

            }

            fun drawable() = entries.firstOrNull { it.symbol == it.cwf.singleBg[0].slopeArrow }?.arrowCustom ?: NONE.arrowCustom
            fun value() = entries.firstOrNull { it.symbol == it.cwf.singleBg[0].slopeArrow }?.dynValue ?: NONE.dynValue
            fun drawableExt1() = entries.firstOrNull { it.symbol == it.cwf.singleBg[1].slopeArrow }?.arrowCustom ?: NONE.arrowCustom
            fun valueExt1() = entries.firstOrNull { it.symbol == it.cwf.singleBg[1].slopeArrow }?.dynValue ?: NONE.dynValue
            fun drawableExt2() = entries.firstOrNull { it.symbol == it.cwf.singleBg[2].slopeArrow }?.arrowCustom ?: NONE.arrowCustom
            fun valueExt2() = entries.firstOrNull { it.symbol == it.cwf.singleBg[2].slopeArrow }?.dynValue ?: NONE.dynValue
        }

        lateinit var cwf: CustomWatchface
        var arrowCustom: Drawable? = null
            get() = field ?: customDrawable?.let { cwf.resDataMap[it.fileName]?.toDrawable(cwf.resources)?.also { arrowCustom = it } } ?: ResourcesCompat.getDrawable(cwf.resources, icon, cwf.theme)
    }

    private enum class GravityMap(val key: String, val gravity: Int) {

        CENTER(JsonKeyValues.CENTER.key, Gravity.CENTER),
        LEFT(JsonKeyValues.LEFT.key, Gravity.START),
        RIGHT(JsonKeyValues.RIGHT.key, Gravity.END);

        companion object {

            fun gravity(key: String?) = entries.firstOrNull { it.key == key }?.gravity ?: CENTER.gravity
            fun key(gravity: Int) = entries.firstOrNull { it.gravity == gravity }?.key ?: CENTER.key
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
                entries.forEach { fontMap ->
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

            fun style(key: String?) = entries.firstOrNull { it.key == key }?.style ?: NORMAL.style
            fun key(style: Int) = entries.firstOrNull { it.style == style }?.key ?: NORMAL.key
        }
    }

    // This class containt mapping between keys used within json of Custom Watchface and preferences
    // Note defaultValue should be identical to default value in xml file,
    // defaultValue Type set to Any for future updates (Boolean or String) (key_digital_style_frame_style, key_digital_style_frame_color are strings...)
    private enum class PrefMap(val key: String, @StringRes val prefKey: Int, val defaultValue: Any, val typeBool: Boolean) {

        SHOW_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_IOB.key, R.string.key_show_iob, true, true),
        SHOW_DETAILED_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_IOB.key, R.string.key_show_detailed_iob, false, true),
        SHOW_COB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COB.key, R.string.key_show_cob, true, true),
        SHOW_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DELTA.key, R.string.key_show_delta, true, true),
        SHOW_AVG_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AVG_DELTA.key, R.string.key_show_avg_delta, true, true),
        SHOW_TEMP_TARGET(CwfMetadataKey.CWF_PREF_WATCH_SHOW_TEMP_TARGET.key, R.string.key_show_temp_target, true, true),
        SHOW_RESERVOIR_LEVEL(CwfMetadataKey.CWF_PREF_WATCH_SHOW_RESERVOIR_LEVEL.key, R.string.key_show_reservoir_level, true, true),
        SHOW_DETAILED_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_DELTA.key, R.string.key_show_detailed_delta, false, true),
        SHOW_UPLOADER_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY.key, R.string.key_show_uploader_battery, true, true),
        SHOW_RIG_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_RIG_BATTERY.key, R.string.key_show_rig_battery, false, true),
        SHOW_TEMP_BASAL(CwfMetadataKey.CWF_PREF_WATCH_SHOW_TEMP_BASAL.key, R.string.key_show_temp_basal, true, true),
        SHOW_DIRECTION(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DIRECTION.key, R.string.key_show_direction, true, true),
        SHOW_AGO(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AGO.key, R.string.key_show_ago, true, true),
        SHOW_BG(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BG.key, R.string.key_show_bg, true, true),
        SHOW_BGI(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BGI.key, R.string.key_show_bgi, false, true),
        SHOW_LOOP_STATUS(CwfMetadataKey.CWF_PREF_WATCH_SHOW_LOOP_STATUS.key, R.string.key_show_external_status, true, true),
        SHOW_WEEK_NUMBER(CwfMetadataKey.CWF_PREF_WATCH_SHOW_WEEK_NUMBER.key, R.string.key_show_week_number, false, true),
        SHOW_DATE(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DATE.key, R.string.key_show_date, false, true),
        SHOW_SECOND(CwfMetadataKey.CWF_PREF_WATCH_SHOW_SECONDS.key, R.string.key_show_seconds, true, true),
        PREF_UNITS(JsonKeyValues.PREF_UNITS.key, R.string.key_units_mgdl, true, true),
        PREF_DARK(JsonKeyValues.PREF_DARK.key, R.string.key_dark, true, true),
        PREF_MATCH_DIVIDER(JsonKeyValues.PREF_MATCH_DIVIDER.key, R.string.key_match_divider, false, true);

        var value: String = ""

        companion object {

            fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        }
    }

    private enum class ValueMap(val key: String, val min: Double, val max: Double) {
        NONE("", 0.0, 0.0),
        SGV(ViewKeys.SGV.key, 39.0, 400.0),
        SGV_LEVEL(JsonKeyValues.SGV_LEVEL.key, -1.0, 1.0),
        DIRECTION(ViewKeys.DIRECTION.key, 1.0, 7.0),
        DELTA(ViewKeys.DELTA.key, -25.0, 25.0),
        AVG_DELTA(ViewKeys.AVG_DELTA.key, -25.0, 25.0),
        TEMP_TARGET(ViewKeys.TEMP_TARGET.key, 0.0, 2.0),
        RESERVOIR(ViewKeys.RESERVOIR.key, 0.0, 500.0),
        RESERVOIR_LEVEL(JsonKeyValues.RESERVOIR_LEVEL.key, 0.0, 2.0),
        UPLOADER_BATTERY(ViewKeys.UPLOADER_BATTERY.key, 0.0, 100.0),
        RIG_BATTERY(ViewKeys.RIG_BATTERY.key, 0.0, 100.0),
        TIMESTAMP(ViewKeys.TIMESTAMP.key, 0.0, 60.0),
        LOOP(ViewKeys.LOOP.key, 0.0, 28.0),
        DAY(ViewKeys.DAY.key, 1.0, 31.0),
        DAY_NAME(ViewKeys.DAY_NAME.key, 1.0, 7.0),
        MONTH(ViewKeys.MONTH.key, 1.0, 12.0),
        WEEK_NUMBER(ViewKeys.WEEK_NUMBER.key, 1.0, 53.0),
        SGV_EXT1(ViewKeys.SGV_EXT1.key, 39.0, 400.0),
        SGV_LEVEL_EXT1(JsonKeyValues.SGV_LEVEL_EXT1.key, -1.0, 1.0),
        DIRECTION_EXT1(ViewKeys.DIRECTION_EXT1.key, 1.0, 7.0),
        DELTA_EXT1(ViewKeys.DELTA_EXT1.key, -25.0, 25.0),
        AVG_DELTA_EXT1(ViewKeys.AVG_DELTA_EXT1.key, -25.0, 25.0),
        TEMP_TARGET_EXT1(ViewKeys.TEMP_TARGET_EXT1.key, 0.0, 2.0),
        RESERVOIR_EXT1(ViewKeys.RESERVOIR_EXT1.key, 0.0, 500.0),
        RESERVOIR_LEVEL_EXT1(JsonKeyValues.RESERVOIR_LEVEL_EXT1.key, 0.0, 2.0),
        RIG_BATTERY_EXT1(ViewKeys.RIG_BATTERY_EXT1.key, 0.0, 100.0),
        TIMESTAMP_EXT1(ViewKeys.TIMESTAMP_EXT1.key, 0.0, 60.0),
        LOOP_EXT1(ViewKeys.LOOP_EXT1.key, 0.0, 28.0),
        SGV_EXT2(ViewKeys.SGV_EXT2.key, 39.0, 400.0),
        SGV_LEVEL_EXT2(JsonKeyValues.SGV_LEVEL_EXT2.key, -1.0, 1.0),
        DIRECTION_EXT2(ViewKeys.DIRECTION_EXT2.key, 1.0, 7.0),
        DELTA_EXT2(ViewKeys.DELTA_EXT2.key, -25.0, 25.0),
        AVG_DELTA_EXT2(ViewKeys.AVG_DELTA_EXT2.key, -25.0, 25.0),
        TEMP_TARGET_EXT2(ViewKeys.TEMP_TARGET_EXT2.key, 0.0, 2.0),
        RESERVOIR_EXT2(ViewKeys.RESERVOIR_EXT2.key, 0.0, 500.0),
        RESERVOIR_LEVEL_EXT2(JsonKeyValues.RESERVOIR_LEVEL_EXT2.key, 0.0, 2.0),
        RIG_BATTERY_EXT2(ViewKeys.RIG_BATTERY_EXT2.key, 0.0, 100.0),
        TIMESTAMP_EXT2(ViewKeys.TIMESTAMP_EXT2.key, 0.0, 60.0),
        LOOP_EXT2(ViewKeys.LOOP_EXT2.key, 0.0, 28.0);

        fun dynValue(dataValue: Double, dataRange: DataRange, valueRange: DataRange): Double = when {
            dataValue < dataRange.minData -> dataRange.minData
            dataValue > dataRange.maxData -> dataRange.maxData
            else                          -> dataValue
        }.let {
            if (dataRange.minData != dataRange.maxData)
                valueRange.minData + (it - dataRange.minData) * (valueRange.maxData - valueRange.minData) / (dataRange.maxData - dataRange.minData)
            else it
        }

        fun stepValue(dataValue: Double, range: DataRange, step: Int): Int = step(dataValue, range, step)
        private fun step(dataValue: Double, dataRange: DataRange, step: Int): Int = when {
            dataValue < dataRange.minData  -> dataRange.minData
            dataValue >= dataRange.maxData -> dataRange.maxData * 0.9999 // to avoid dataValue == maxData and be out of range
            else                           -> dataValue
        }.let { if (dataRange.minData != dataRange.maxData) (1 + ((it - dataRange.minData) * step) / (dataRange.maxData - dataRange.minData)).toInt() else 0 }

        companion object {

            fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: NONE
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
        private val dynTextValue = mutableMapOf<Int, String>()
        private var dataRange: DataRange? = null
        private var topRange: DataRange? = null
        private var leftRange: DataRange? = null
        private var dynRange: DataRange? = null
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
        val stepTextValue: Int
            get() = dynTextValue[0]?.let { dynTextValue.size - 1 } ?: dynTextValue.size

        val dataValue: Double?
            get() = when (valueMap) {
                ValueMap.NONE             -> 0.0
                ValueMap.SGV              -> if (cwf.singleBg[0].sgvString != "---") cwf.singleBg[0].sgv else null
                ValueMap.SGV_LEVEL        -> if (cwf.singleBg[0].sgvString != "---") cwf.singleBg[0].sgvLevel.toDouble() else null
                ValueMap.DIRECTION        -> TrendArrowMap.value()
                ValueMap.DELTA            -> cwf.singleBg[0].deltaMgdl
                ValueMap.AVG_DELTA        -> cwf.singleBg[0].avgDeltaMgdl
                ValueMap.TEMP_TARGET      -> cwf.status[0].tempTargetLevel.toDouble()
                ValueMap.RESERVOIR        -> cwf.status[0].reservoir
                ValueMap.RESERVOIR_LEVEL  -> cwf.status[0].reservoirLevel.toDouble()
                ValueMap.RIG_BATTERY      -> cwf.status[0].rigBattery.replace("%", "").toDoubleOrNull()
                ValueMap.UPLOADER_BATTERY -> cwf.status[0].battery.replace("%", "").toDoubleOrNull()
                ValueMap.LOOP             -> if (cwf.status[0].openApsStatus != -1L) ((System.currentTimeMillis() - cwf.status[0].openApsStatus) / 1000 / 60).toDouble() else null
                ValueMap.TIMESTAMP        -> if (cwf.singleBg[0].timeStamp != 0L) floor(cwf.timeSince() / (1000 * 60)) else null
                ValueMap.DAY              -> LocalDateTime.now().dayOfMonth.toDouble()
                ValueMap.DAY_NAME         -> LocalDateTime.now().dayOfWeek.value.toDouble()
                ValueMap.MONTH            -> LocalDateTime.now().monthValue.toDouble()
                ValueMap.WEEK_NUMBER      -> LocalDateTime.now().get(WeekFields.ISO.weekOfWeekBasedYear()).toDouble()
                ValueMap.SGV_EXT1         -> if (cwf.singleBg[1].sgvString != "---") cwf.singleBg[1].sgv else null
                ValueMap.SGV_LEVEL_EXT1   -> if (cwf.singleBg[1].sgvString != "---") cwf.singleBg[1].sgvLevel.toDouble() else null
                ValueMap.DIRECTION_EXT1   -> TrendArrowMap.valueExt1()
                ValueMap.DELTA_EXT1       -> cwf.singleBg[1].deltaMgdl
                ValueMap.AVG_DELTA_EXT1   -> cwf.singleBg[1].avgDeltaMgdl
                ValueMap.TEMP_TARGET_EXT1 -> cwf.status[1].tempTargetLevel.toDouble()
                ValueMap.RESERVOIR_EXT1   -> cwf.status[1].reservoir
                ValueMap.RESERVOIR_LEVEL_EXT1 -> cwf.status[1].reservoirLevel.toDouble()
                ValueMap.RIG_BATTERY_EXT1 -> cwf.status[1].rigBattery.replace("%", "").toDoubleOrNull()
                ValueMap.LOOP_EXT1        -> if (cwf.status[1].openApsStatus != -1L) ((System.currentTimeMillis() - cwf.status[1].openApsStatus) / 1000 / 60).toDouble() else null
                ValueMap.TIMESTAMP_EXT1   -> if (cwf.singleBg[1].timeStamp != 0L) floor(cwf.timeSince(1) / (1000 * 60)) else null
                ValueMap.SGV_EXT2         -> if (cwf.singleBg[2].sgvString != "---") cwf.singleBg[2].sgv else null
                ValueMap.SGV_LEVEL_EXT2   -> if (cwf.singleBg[2].sgvString != "---") cwf.singleBg[2].sgvLevel.toDouble() else null
                ValueMap.DIRECTION_EXT2   -> TrendArrowMap.valueExt2()
                ValueMap.DELTA_EXT2       -> cwf.singleBg[2].deltaMgdl
                ValueMap.AVG_DELTA_EXT2   -> cwf.singleBg[2].avgDeltaMgdl
                ValueMap.TEMP_TARGET_EXT2 -> cwf.status[2].tempTargetLevel.toDouble()
                ValueMap.RESERVOIR_EXT2   -> cwf.status[2].reservoir
                ValueMap.RESERVOIR_LEVEL_EXT2 -> cwf.status[2].reservoirLevel.toDouble()
                ValueMap.RIG_BATTERY_EXT2 -> cwf.status[2].rigBattery.replace("%", "").toDoubleOrNull()
                ValueMap.LOOP_EXT2        -> if (cwf.status[2].openApsStatus != -1L) ((System.currentTimeMillis() - cwf.status[2].openApsStatus) / 1000 / 60).toDouble() else null
                ValueMap.TIMESTAMP_EXT2   -> if (cwf.singleBg[2].timeStamp != 0L) floor(cwf.timeSince(2) / (1000 * 60)) else null
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

        fun getRotationOffset(): Int = dataRange?.let { dataRange -> rotationRange?.let { rotRange -> dataValue?.let { valueMap.dynValue(it, dataRange, rotRange).toInt() } ?: rotRange.invalidData } } ?: 0
        fun getDrawable() = dataRange?.let { dataRange -> dataValue?.let { dynDrawable[valueMap.stepValue(it, dataRange, stepDraw)] } ?: dynDrawable[0] }
        fun getFontColorStep() = getIntValue(dynFontColor, stepFontColor)
        fun getTextSizeStep() = getIntValue(dynTextSize, stepTextSize)
        fun getColorStep() = getIntValue(dynColor, stepColor)
        fun getTopOffsetStep() = getIntValue(dynTopOffset, stepTopOffset)
        fun getLeftOffsetStep() = getIntValue(dynLeftOffset, stepLeftOffset)
        fun getRotationOffsetStep() = getIntValue(dynRotationOffset, stepRotationOffset)
        fun getTextValueStep() = dataRange?.let { dataRange -> dataValue?.let { dynTextValue[valueMap.stepValue(it, dataRange, stepTextValue)] } ?: dynTextValue[0] }
        fun getDynValue(range: Boolean): Double? =
            if (range)
                dataRange?.let { dataRange ->
                    dynRange?.let { dynRange ->
                        dataValue?.let { (valueMap.dynValue(it, dataRange, dynRange)) }
                            ?: (dynRange.invalidData.toDouble())
                    }
                } ?: dataValue
            else
                dataValue

        private fun getIntValue(dynMap: MutableMap<Int, Int>, step: Int) =
            if (step > 0) dataRange?.let { dataRange -> dataValue?.let { dynMap[valueMap.stepValue(it, dataRange, step)] } ?: dynMap[0] ?: dynMap[1] } else null

        private fun load() {
            DataRange(dataJson.optDouble(JsonKeys.MINDATA.key, valueMap.min), dataJson.optDouble(JsonKeys.MAXDATA.key, valueMap.max)).let { defaultRange ->
                dataRange = defaultRange
                dynRange = parseDataRange(dataJson.optJSONObject(JsonKeys.DYNVALUE.key), defaultRange)
                topRange = parseDataRange(dataJson.optJSONObject(JsonKeys.TOPOFFSET.key), defaultRange)
                leftRange = parseDataRange(dataJson.optJSONObject(JsonKeys.LEFTOFFSET.key), defaultRange)
                rotationRange = parseDataRange(dataJson.optJSONObject(JsonKeys.ROTATIONOFFSET.key), defaultRange)
            }
            getDrawableSteps(dynDrawable, JsonKeys.IMAGE.key, JsonKeys.INVALIDIMAGE.key)
            getColorSteps(dynColor, JsonKeys.COLOR.key, JsonKeys.INVALIDCOLOR.key)
            getColorSteps(dynFontColor, JsonKeys.FONTCOLOR.key, JsonKeys.INVALIDFONTCOLOR.key)
            getIntSteps(dynTextSize, JsonKeys.TEXTSIZE.key, JsonKeys.INVALIDTEXTSIZE.key)
            getIntSteps(dynLeftOffset, JsonKeys.LEFTOFFSET.key, JsonKeys.INVALIDLEFTOFFSET.key)
            getIntSteps(dynTopOffset, JsonKeys.TOPOFFSET.key, JsonKeys.INVALIDTOPOFFSET.key)
            getIntSteps(dynRotationOffset, JsonKeys.ROTATIONOFFSET.key, JsonKeys.INVALIDROTATIONOFFSET.key)
            getStringSteps(dynTextValue, JsonKeys.TEXTVALUE.key, JsonKeys.INVALIDTEXTVALUE.key)
        }

        private fun getDrawableSteps(dynMap: MutableMap<Int, Drawable?>, key: String, invalidKey: String) {
            if (dataJson.has(invalidKey))
                dynMap[0] = dataJson.optString(invalidKey).let { cwf.resDataMap[it]?.toDrawable(cwf.resources, width, height) }
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

        private fun getStringSteps(dynMap: MutableMap<Int, String>, key: String, invalidKey: String) {
            if (dataJson.has(invalidKey))
                dynMap[0] = dataJson.optString(invalidKey)
            var idx = 1
            while (dataJson.has("${key}$idx")) {
                dynMap[idx] = dataJson.optString("${key}$idx")
                idx++
            }
        }

        private fun parseDataRange(json: JSONObject?, defaultData: DataRange) =
            json?.let {
                DataRange(
                    minData = it.optDouble(JsonKeys.MINVALUE.key, defaultData.minData),
                    maxData = it.optDouble(JsonKeys.MAXVALUE.key, defaultData.maxData),
                    invalidData = it.optInt(JsonKeys.INVALIDVALUE.key, defaultData.invalidData)
                )
            } ?: defaultData

        companion object {

            val dynData = mutableMapOf<String, DynProvider>()
            var dynJson: JSONObject? = null
            fun init(cwf: CustomWatchface, json: JSONObject?) {
                cwf.dynPref(json?.optJSONObject((JsonKeys.DYNPREF.key)))
                this.dynJson = json?.optJSONObject((JsonKeys.DYNDATA.key))
                dynData.clear()
            }

            fun getDyn(cwf: CustomWatchface, keyPref: String, key: String, width: Int, height: Int, defaultViewKey: String): DynProvider? {
                if (dynData[defaultViewKey] != null)
                    return dynData[defaultViewKey]

                cwf.dynPref[keyPref]?.let { dynPref ->
                    ValueMap.fromKey(dynPref.optString(JsonKeys.VALUEKEY.key, defaultViewKey)).let { valueMap ->
                        DynProvider(cwf, dynPref, valueMap, width, height).also { it.load() }
                    }
                }?.also { dynData[defaultViewKey] = it }

                if (dynData[defaultViewKey] != null)
                    return dynData[defaultViewKey]

                dynJson?.optJSONObject(key)?.let { dynJson ->
                    ValueMap.fromKey(dynJson.optString(JsonKeys.VALUEKEY.key, defaultViewKey)).let { valueMap ->
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
        val prefKey = json.optString(JsonKeys.PREFKEY.key)
        PrefMap.fromKey(prefKey)?.let { prefMap ->
            val value = valPref[prefMap.key]
                ?: (if (prefMap.typeBool) sp.getBoolean(prefMap.prefKey, prefMap.defaultValue as Boolean).toString() else sp.getString(prefMap.prefKey, prefMap.defaultValue as String)).also {
                    valPref[prefMap.key] = it
                }
            json.optJSONObject(value)?.let { nextJson ->
                if (nextJson.has(JsonKeys.DYNPREF.key)) {
                    nextJson.keys().forEach { key ->
                        if (key != JsonKeys.DYNPREF.key)
                            targetJson.putOpt(key, nextJson.opt(key))
                    }
                    val nextKey = nextJson.optString(JsonKeys.DYNPREF.key)
                    if (nextKey.isNotEmpty() && nextKey !in visitedKeys) {
                        visitedKeys += nextKey
                        dynJson.optJSONObject(nextKey)?.let {
                            buildDynPrefs(dynJson, targetJson, it, key, visitedKeys)
                        }
                    }
                } else {
                    nextJson.keys().forEach { key ->
                        if (key != JsonKeys.DYNPREF.key)
                            targetJson.putOpt(key, nextJson.opt(key))
                    }
                    dynPref[key] = targetJson
                }
            }
        }
    }

    private fun checkPref() = valPref.any { (prefMap, s) ->
        s != PrefMap.fromKey(prefMap)?.let { if (it.typeBool) sp.getBoolean(it.prefKey, it.defaultValue as Boolean).toString() else sp.getString(it.prefKey, it.defaultValue as String) }
    }
}


