package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
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
import app.aaps.wear.utils.toVisibility
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
import app.aaps.wear.watchfaces.utils.ComplicationImageFit
import app.aaps.wear.watchfaces.utils.ComplicationRender
import app.aaps.wear.watchfaces.utils.ComplicationSlotInfo
import app.aaps.wear.watchfaces.utils.ComplicationStyleValues
import app.aaps.wear.watchfaces.utils.ComplicationTypePriority
import app.aaps.wear.watchfaces.utils.WatchFaceComplicationSlots
import app.aaps.wear.watchfaces.utils.WatchFaceComplications
import app.aaps.wear.watchfaces.utils.WatchFaceSettingRow
import app.aaps.wear.watchfaces.utils.WatchFaceSettings
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlin.collections.get
import kotlin.math.floor

@SuppressLint("Deprecated")
class CustomWatchface : BaseWatchFace() {

    @Inject lateinit var context: Context
    private lateinit var binding: ActivityCustomBinding
    private var zoomFactor = 1.0
    private var lowBatColor = Color.RED
    private var resDataMap: CwfResDataMap = mutableMapOf()
    private var json = JSONObject()
    private var jsonString = ""
    /**
     * A real [ImageView] for background, deliberately not part of `activity_custom.xml`: background
     * must paint before complications and everything in that layout paints after them (see
     * [deferMainLayoutDraw]). Unattached - measured and laid out by hand in
     * [resolveBackgroundDrawable], drawn onto the Canvas in [onDraw] - so background goes through the
     * same [ViewMap.customizeImageView] as every other image.
     *
     * Per engine instance, never enum state: the editor runs a second, headless CustomWatchface.
     */
    private val backgroundView by lazy { ImageView(this) }

    /**
     * The complication types slot [slotId] accepts, in preference order - see
     * [ComplicationTypePriority].
     *
     * The priority comes from preferences, not the CWF json: the negotiated type is stored by the
     * system per (watch face, slot) and survives CWF reloads, so it is user state. A slot's own
     * setting wins, `null` means "use the default one".
     *
     * Read at slot creation only, but a change does not need the engine recreated - the editor builds
     * its own instance when the picker opens. It does need the data source to actually change, since
     * the system skips renegotiation when the same provider is picked again.
     */
    private fun complicationSupportedTypes(slotId: Int): List<ComplicationType> {
        val slot = ComplicationMap.entries.firstOrNull { it.id == slotId }
        val priority = slot?.let { typePriority(it.typePriorityPrefKey) } ?: typePriority(null)
        return priority.supportedTypes()
    }

    /**
     * The priority stored under [prefKey], falling back to the all-slots default and then to
     * [ComplicationTypePriority.VALUE].
     */
    private fun typePriority(@StringRes prefKey: Int?): ComplicationTypePriority {
        val stored = prefKey?.let { sp.getString(it, USE_DEFAULT_TYPE_PRIORITY) }?.takeIf { it != USE_DEFAULT_TYPE_PRIORITY }
            ?: sp.getString(R.string.key_complication_type_priority_default, ComplicationTypePriority.VALUE.name)
        return ComplicationTypePriority.entries.firstOrNull { it.name == stored } ?: ComplicationTypePriority.VALUE
    }


    /**
     * The generic complication plumbing for *this* engine instance - see [WatchFaceComplications].
     * The slot list comes straight from [ComplicationMap], so adding a slot means one new entry there
     * (plus the [ViewMap] entry carrying its geometry) and nothing here.
     *
     * An instance field, never enum state: it holds live objects bound to this one engine (a
     * `ComplicationDrawable` with its renderer and invalidate callback), and the editor runs a second,
     * headless CustomWatchface at the same time. That is also why [ComplicationMap] carries slot
     * identity only and never a drawable.
     */
    private val complications = WatchFaceComplications(
        context = this,
        slotIds = complicationSlots.map { it.id },
        supportedTypesFor = ::complicationSupportedTypes,
        layoutSettingLabel = R.string.cwf_complication_layout,
        layoutSettingDescription = R.string.cwf_complication_layout_description
    )

    /** The CWF-wide `complicationStyle` values, decoded once per [setWatchfaceStyle] pass. */
    private val complicationStyle = ComplicationGlobalStyle(this)

    // internal, not public: [complicationSlots] hands out an internal type.
    internal companion object : WatchFaceComplicationSlots, WatchFaceSettings {

        /**
         * This watch face's half of the [WatchFaceComplicationSlots] contract: one data source picker
         * per slot, in screen order. Keeps [ViewMap] and the CWF json out of the settings screens.
         */
        override val complicationSlots: List<ComplicationSlotInfo> = ComplicationMap.entries

        /**
         * This watch face's settings screen, built from the enums that already define its behaviour
         * rather than from a settings xml kept in step by hand.
         *
         * Which rows are relevant depends on the loaded CWF, and deciding that is this class's job -
         * only this class may read the CWF json.
         */
        override fun settingRows(storedConfiguration: String?): List<WatchFaceSettingRow> {
            val json = storedConfiguration?.let {
                try {
                    JSONObject(it)
                } catch (_: JSONException) {
                    null
                }
            }
            return buildList {
                // Rows about what the dial shows: kept only while the CWF has something for them to
                // act on.
                listOf(PrefMap.SHOW_SECOND, PrefMap.SHOW_WEEK_NUMBER, PrefMap.PREF_DARK, PrefMap.PREF_MATCH_DIVIDER, PrefMap.SHOW_DATE)
                    .filter { it.isUsedBy(json) }
                    .forEach { pref -> pref.toggle()?.let { add(it) } }
                // Meaningless unless the CWF draws external views. Keyed on the views themselves,
                // not on a PrefMap: no single preference gates them, they carry [ViewMap.external].
                if (json == null || ViewMap.entries.any { it.external > 0 && it.isShownBy(json) })
                    add(WatchFaceSettingRow.Toggle(R.string.key_switch_external, R.string.pref_switch_external, false))
                // Each slot the CWF draws contributes a pair: the toggle that shows it, then the
                // picker that chooses what it shows, greyed while the toggle is off.
                ComplicationMap.entries.filter { it.showPref.isUsedBy(json) }.forEach { slot ->
                    slot.showPref.toggle()?.let { add(it) }
                    add(WatchFaceSettingRow.Action(slot.preferenceKey, slot.preferenceTitle, dependencyKey = slot.showPref.prefKey))
                }
                // Never filtered: it configures the system's provider binding, not the template.
                add(WatchFaceSettingRow.SubScreen(R.string.key_complication_type_priority_screen, R.string.pref_complication_type_priority))
                // Never filtered either: one decides how data is presented, the other what an
                // exported template contains, so no CWF can make them irrelevant.
                add(
                    WatchFaceSettingRow.Choice(
                        key = R.string.key_simplify_ui,
                        title = R.string.pref_simplify_ui,
                        entries = R.array.watchface_simplify_ui_name,
                        entryValues = R.array.watchface_simplify_ui_values,
                        // The same literal SimpleUi.isEnabled() defaults to when the key is unset.
                        defaultValue = "off"
                    )
                )
                add(WatchFaceSettingRow.Toggle(R.string.key_include_external, R.string.pref_include_external, false))
            }
        }

        /**
         * This preference as an on/off row. Null for a preference with no label, which is one with no
         * row at all (see [PrefMap.title]).
         */
        private fun PrefMap.toggle(): WatchFaceSettingRow.Toggle? =
            title?.let { WatchFaceSettingRow.Toggle(prefKey, it, defaultValue as Boolean) }

        /**
         * The "Complication type priority" screen: a warning, one default for every slot, then one
         * override per slot. Every slot is listed whatever the loaded CWF draws, since this configures
         * the system's provider binding rather than the template.
         */
        override fun subScreenRows(@StringRes subScreenKey: Int): List<WatchFaceSettingRow> {
            if (subScreenKey != R.string.key_complication_type_priority_screen) return emptyList()
            return buildList {
                add(WatchFaceSettingRow.Info(R.string.key_complication_type_priority_warning, R.string.pref_complication_type_priority_warning))
                add(
                    WatchFaceSettingRow.Choice(
                        key = R.string.key_complication_type_priority_default,
                        title = R.string.pref_complication_type_priority_all,
                        entries = R.array.complication_type_priority_name,
                        entryValues = R.array.complication_type_priority_values,
                        defaultValue = ComplicationTypePriority.VALUE.name
                    )
                )
                ComplicationMap.entries.forEach { slot ->
                    add(
                        WatchFaceSettingRow.Choice(
                            key = slot.typePriorityPrefKey,
                            title = slot.preferenceTitle,
                            entries = R.array.complication_type_priority_slot_name,
                            entryValues = R.array.complication_type_priority_slot_values,
                            defaultValue = USE_DEFAULT_TYPE_PRIORITY
                        )
                    )
                }
            }
        }

        /**
         * Whether the loaded CWF gives this preference anything to act on: a view it gates that the
         * zip asks to show, or a `dynPref` block keyed on it. The second arm matters - a CWF often
         * drives colours from a preference no view names, `key_dark` through one dynPref block being
         * the usual case.
         *
         * No json - nothing loaded, or unparsable - keeps every row.
         */
        private fun PrefMap.isUsedBy(json: JSONObject?): Boolean {
            json ?: return true
            return ViewMap.entries.any { it.visibilityPref == this && it.isShownBy(json) } ||
                json.optJSONObject(JsonKeys.DYNPREF.key).keysOn(key)
        }

        /**
         * Whether the CWF declares this view and asks for it to be shown. Reads the json alone, never
         * [ViewMap.prefVisibility] - a row that vanished when the user switched it off could never be
         * switched back on.
         */
        private fun ViewMap.isShownBy(json: JSONObject): Boolean =
            json.optJSONObject(key)?.optString(JsonKeys.VISIBILITY.key, JsonKeyValues.GONE.key) == JsonKeyValues.VISIBLE.key

        /** Whether this json, or any object nested in it, is a `dynPref` block keyed on [prefKey]. */
        private fun JSONObject?.keysOn(prefKey: String): Boolean {
            val json = this ?: return false
            if (json.optString(JsonKeys.PREFKEY.key) == prefKey) return true
            return json.keys().asSequence().any { json.optJSONObject(it).keysOn(prefKey) }
        }

        /**
         * The fixed coordinate space every CWF json declares its geometry in. A companion constant so
         * code with no [CustomWatchface] instance to hand can reach it.
         */
        private const val TEMPLE_RESOLUTION = 400

        /**
         * A typeface combining a `FONT` key with a `FONTSTYLE` one, for the complication properties
         * that take a single [Typeface] and have no separate style argument.
         *
         * Not fully equivalent to `TextView.setTypeface(tf, style)` for a single-weight custom font:
         * `TextView` also sets `fakeBoldText`/`textSkewX` on its paint, and `ComplicationStyle`
         * exposes no paint, so bold on a one-weight font resource yields the unstyled face.
         */
        private fun typefaceOf(json: JSONObject?, fontKey: String, styleKey: String, default: Typeface): Typeface {
            val base = json?.optString(fontKey)?.takeIf { it.isNotEmpty() }?.let { FontMap.font(it) } ?: default
            val style = json?.optString(styleKey)?.takeIf { it.isNotEmpty() }?.let { StyleMap.style(it) } ?: return base
            return Typeface.create(base, style)
        }


        /**
         * Ring thickness used when a CWF declares no `RINGWIDTH`, in the CWF's 400x400 space like the
         * key itself. Eyeballed against an OEM watch face, not an upstream guideline - see
         * [ViewMap.ringWidthPx]. A CWF wanting the ring to scale with the slot sets `RINGWIDTH`.
         */
        private const val PROVISIONAL_RING_WIDTH = 9

        /**
         * Border thickness used when a CWF sets `BORDERCOLOR` but no `BORDERWIDTH`, in the CWF's
         * 400x400 space like every other dimension. Close to androidx's own effective default of
         * `1dp`. Safe to tune: it takes part in no layout decision.
         */
        private const val DEFAULT_BORDER_WIDTH = 2

        /**
         * Value meaning "this slot follows the all-slots default", and the initial value of every
         * per-slot type priority. Not a [ComplicationTypePriority] name, so "follow the default" stays
         * distinguishable from "happens to match it today".
         */
        internal const val USE_DEFAULT_TYPE_PRIORITY = "default"


    }

    override fun createUserStyleSchema(): UserStyleSchema = complications.createUserStyleSchema()

    // Must never throw: this can run before BaseWatchFace's Dagger injection has completed, and it is
    // the first thing the framework calls on a headless instance, which never gets an onCreate. Hence
    // ensureInjected() first, so the editor's headless instance builds slots from the real CWF json.
    // daggerInjectionComplete is checked rather than catching UninitializedPropertyAccessException,
    // which would also mask unrelated lateinit bugs; with no injection we fall back to each slot's
    // default bounds, same as a fresh install.
    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        ensureInjected()
        // Read here rather than in WatchFaceComplications: ensureInjected / daggerInjectionComplete
        // are protected members of BaseWatchFace. The generic layer only needs the resulting bounds.
        val storedJson = if (daggerInjectionComplete) {
            runBlocking {
                complicationDataRepository.getCustomWatchface() ?: complicationDataRepository.getCustomWatchface(true)
            }?.json?.let { JSONObject(it) }
        } else {
            null
        }
        return complications.createSlotsManager(currentUserStyleRepository) { slotId ->
            slotBounds(storedJson?.optJSONObject(ViewMap.entries.firstOrNull { it.complication?.id == slotId }?.key))
        }
    }

    /**
     * Bounds/visibility/rotation for [slot] as of *this* frame, so a complication behaves like every
     * other CWF-driven view instead of being frozen at what the json said when the engine was created.
     *
     * Nothing is recomputed here: `customizeViewCommon` already re-derives the placeholder
     * [FrameLayout]'s size, margins, rotation and visibility from the current json plus [DynProvider],
     * and [BaseWatchFace.onDraw] re-lays-out `mainLayout` every frame before complications are drawn.
     * Reading the laid-out placeholder therefore inherits every dynamic behaviour the other views have.
     *
     * Falls back to [ComplicationRender.Declared] when there is no usable layout to read - the editor's
     * headless instance never inflates one.
     */
    override fun complicationRender(slot: ComplicationSlot): ComplicationRender {
        val view = complicationPlaceholder(slot.id) ?: return ComplicationRender.Declared
        if (view.width == 0 || view.height == 0) return ComplicationRender.Declared
        if (!view.isVisible) return ComplicationRender.Skip
        // simpleUi replaces the whole layout (BaseWatchFace.drawMainLayout skips it), so painting
        // complications over it would leave them floating on a screen nothing else is drawn on.
        if (simpleUi.isEnabled(currentWatchMode)) return ComplicationRender.Skip
        return ComplicationRender.At(Rect(view.left, view.top, view.right, view.bottom), view.rotation)
    }

    /**
     * Placeholder views by slot id, resolved once per inflate. Cached because the lookup is on the
     * render path - per slot per frame, plus `syncSlotGeometry` - and `findViewById` would walk a
     * ~90-view tree several times a frame. Empty before inflate, which every caller already handles.
     */
    private var complicationPlaceholders: Map<Int, FrameLayout> = emptyMap()

    private fun complicationPlaceholder(slotId: Int): FrameLayout? = complicationPlaceholders[slotId]

    /**
     * Where [slotId] is actually drawn this frame, as fractional unit-square (canvas-relative) bounds,
     * or null when hidden. Handed to [WatchFaceComplications.syncGeometry] so the slot's *declared*
     * bounds - what taps are tested against - follow what the CWF draws.
     *
     * Read from the laid-out placeholder, not recomputed from json, so any `DynProvider` offset is
     * already included.
     */
    private fun visibleSlotBounds(slotId: Int): RectF? {
        val width = getWidth().toFloat()
        val height = getHeight().toFloat()
        if (width <= 0f || height <= 0f) return null
        val view = complicationPlaceholder(slotId)?.takeIf { it.isVisible && it.width > 0 && it.height > 0 } ?: return null
        return RectF(view.left / width, view.top / height, view.right / width, view.bottom / height)
    }

    /**
     * The one and only way a slot's **declared** bounds are produced from CWF json; the no-json
     * fallback lives inside, so there is no second "default bounds" to choose between.
     *
     * `ComplicationSlotBounds` are fractional unit-square (canvas-relative) while the json's
     * WIDTH/HEIGHT/TOPMARGIN/LEFTMARGIN are in the fixed 400x400 [TEMPLE_RESOLUTION] space, so dividing
     * by it converts directly - `zoomFactor` cancels out, a fraction being resolution-independent.
     *
     * The fallback is an empty rect on purpose: a slot no CWF has positioned must draw nothing and be
     * hit-tested against nothing, rather than showing a box somewhere no watch face asked for.
     *
     * A fresh `RectF` per call, never a stored one: `ComplicationSlotBounds(bounds)` associates the
     * same instance with every type and never copies it.
     */
    private fun slotBounds(slotJson: JSONObject?): ComplicationSlotBounds {
        val width = (slotJson?.optInt(JsonKeys.WIDTH.key) ?: 0) / TEMPLE_RESOLUTION.toFloat()
        val height = (slotJson?.optInt(JsonKeys.HEIGHT.key) ?: 0) / TEMPLE_RESOLUTION.toFloat()
        if (width <= 0f || height <= 0f) return ComplicationSlotBounds(RectF(0f, 0f, 0f, 0f))
        val left = (slotJson?.optInt(JsonKeys.LEFTMARGIN.key) ?: 0) / TEMPLE_RESOLUTION.toFloat()
        val top = (slotJson?.optInt(JsonKeys.TOPMARGIN.key) ?: 0) / TEMPLE_RESOLUTION.toFloat()
        return ComplicationSlotBounds(RectF(left, top, left + width, top + height))
    }

    /**
     * The CWF-wide `complicationStyle` json section, decoded once per [setWatchfaceStyle] pass.
     *
     * A transversal section like `metadata` or `dynPref`, so it is decoded here, outside [ViewMap], at
     * the level the json places it. Per-slot keys stay in [ViewMap.customizeComplicationView]; these
     * are only the values a slot falls back to when it declares nothing of its own.
     */
    private class ComplicationGlobalStyle(private val cwf: CustomWatchface) {

        private var json: JSONObject? = null

        /**
         * `DYNPREF`/`DYNDATA` declared on the section itself, so one CWF-wide value (BG level, say) can
         * drive complication style across every slot at once. Only font colour, background colour and
         * text size can be driven - `DynProvider` exposes a per-step getter for no other value.
         */
        private var dynData: DynProvider? = null

        /**
         * Adopts the `complicationStyle` section of a newly loaded CWF, or null when it declares none -
         * every value below then resolves to its own default, so a CWF without the section resets
         * cleanly instead of inheriting the previous one's style.
         */
        fun init(json: JSONObject?) {
            this.json = json
            // Built here rather than by customizeViewCommon, which only serves entries with a View.
            // 0,0 for width/height is safe while nothing here is drawable-backed - those arguments only
            // scale drawable steps - so wiring BACKGROUND in would need a real size first.
            //
            // COMPLICATIONSTYLE's key doubles as getDyn's `defaultViewKey`, and no ValueMap is named
            // "complicationStyle", so a global dyn block must declare VALUEKEY explicitly: unlike a
            // per-view block it has no view name to fall back on.
            dynData = DynProvider.getDyn(
                cwf, json?.optString(JsonKeys.DYNPREF.key) ?: "", json?.optString(JsonKeys.DYNDATA.key) ?: "",
                0, 0, JsonKeys.COMPLICATIONSTYLE.key
            )
        }

        // Computed on every read, like ViewMap's own visibility()/drawable(), so a value always
        // reflects the loaded CWF and the live DynProvider step. Each chain ends in a concrete default,
        // never null: the ComplicationDrawable outlives the CWF and would keep the previous value.
        val fontColor: Int
            get() = dynData?.getFontColorStep(cwf) ?: cwf.getColor(json?.optString(JsonKeys.FONTCOLOR.key) ?: "", Color.WHITE)

        val titleColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.FONTTITLECOLOR.key) ?: "", Color.LTGRAY)

        /** Falls back to [fontColor]. */
        val iconColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.ICONCOLOR.key) ?: "", fontColor)

        val backgroundColor: Int
            get() = dynData?.getColorStep(cwf)
                ?: json?.let { if (it.has(JsonKeys.COLOR.key)) cwf.getColor(it.optString(JsonKeys.COLOR.key)) else null }
                ?: Color.TRANSPARENT

        val font: Typeface
            get() = typefaceOf(json, JsonKeys.FONT.key, JsonKeys.FONTSTYLE.key, FontMap.DEFAULT.font)

        val titleFont: Typeface
            get() = typefaceOf(json, JsonKeys.FONTTITLE.key, JsonKeys.TITLESTYLE.key, FontMap.DEFAULT.font)

        val borderRadius: Int
            get() = ((json?.optInt(JsonKeys.BORDERRADIUS.key) ?: 0) * cwf.zoomFactor).toInt().coerceAtLeast(0)

        /** Transparent when absent, which is what "no border" means - see [ComplicationStyleValues.borderColor]. */
        val borderColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.BORDERCOLOR.key) ?: "", Color.TRANSPARENT)

        val borderWidth: Int
            get() = ((json?.optInt(JsonKeys.BORDERWIDTH.key)?.takeIf { it > 0 } ?: DEFAULT_BORDER_WIDTH) * cwf.zoomFactor)
                .toInt().coerceAtLeast(0)

        val textSize: Int
            get() = (dynData?.getTextSizeStep(cwf) ?: json?.optInt(JsonKeys.TEXTSIZE.key)?.takeIf { it > 0 })
                ?.let { (it * cwf.zoomFactor).toInt() } ?: ComplicationStyleValues.TEXT_SIZE_FIT_BOX

        val titleSize: Int
            get() = json?.optInt(JsonKeys.TITLESIZE.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: ComplicationStyleValues.TEXT_SIZE_FIT_BOX

        /**
         * Nullable, unlike every other value here: absent means "no width was given", which
         * [ViewMap.ringWidthPx] answers with [PROVISIONAL_RING_WIDTH]. The pixel value it produces is
         * always written, so nothing persists across CWF loads.
         */
        val ringWidth: Int?
            get() = json?.optInt(JsonKeys.RINGWIDTH.key)?.takeIf { it > 0 }

        val ringPrimaryColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.RINGPRIMARYCOLOR.key) ?: "", Color.WHITE)

        val ringSecondaryColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.RINGSECONDARYCOLOR.key) ?: "", ComplicationStyleValues.RING_SECONDARY_COLOR_DEFAULT)

        /** Defaults to the library's own behaviour. */
        val imageFit: ComplicationImageFit
            get() = ImageFitMap.fit(json?.optString(JsonKeys.IMAGEFIT.key), ComplicationImageFit.FIT_CENTER)

        /**
         * Whether the CWF-wide block names an icon colour at all - not which one. Separate from
         * [iconColor] because only an explicit request justifies dropping a provider's own small image
         * so the tintable icon can be used instead.
         */
        val iconColorRequested: Boolean
            get() = json?.optString(JsonKeys.ICONCOLOR.key)?.isNotEmpty() == true
    }

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
        // Resolved from the ViewMap entries that declare a slot id, so a new slot needs no change
        // here - only its entry and the matching FrameLayout in activity_custom.xml.
        complicationPlaceholders = ViewMap.entries
            .mapNotNull { view -> view.complication?.let { slot -> binding.root.findViewById<FrameLayout>(view.id)?.let { slot.id to it } } }
            .toMap()
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
        zoomFactor = displayWidth.toDouble() / TEMPLE_RESOLUTION.toDouble()
        return binding
    }

    // Always update at 1 second intervals for smooth analog clock hand movement
    override fun getInteractiveModeUpdateRate(): Long = 1000L

    // Defers mainLayout's own draw until after complications have rendered, so complications land
    // behind cover_chart and the analog hands instead of on top of them. background is not part of
    // mainLayout at all (see resolveBackgroundDrawable()/onDraw()), so it paints first.
    override fun deferMainLayoutDraw(): Boolean = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Nothing of the CWF is drawn in simpleUi mode - mainLayout is skipped by drawMainLayout() and
        // complications by complicationRender(). background is drawn here rather than inside
        // mainLayout, so it needs the same check or it would paint over the simple UI.
        if (simpleUi.isEnabled(currentWatchMode)) return
        // Draws the view's background colour and its image. View.draw() does not care that the view
        // has no parent, only that resolveBackgroundDrawable() measured and laid it out.
        backgroundView.draw(canvas)
        // super.onDraw() has just laid out mainLayout, so the placeholders hold this frame's geometry
        // and the slots' declared bounds can follow it. Not in complicationRender(): that runs per
        // slot, while one pushed option covers every slot at once.
        complications.syncGeometry(::visibleSlotBounds)
    }

    override fun onDrawOverlay(canvas: Canvas) {
        drawMainLayout(canvas)
    }

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
        binding.direction.setImageDrawable(TrendArrowMap.drawable(singleBg[0]))
        binding.directionExt1.setImageDrawable(TrendArrowMap.drawable(singleBg[1]))
        binding.directionExt2.setImageDrawable(TrendArrowMap.drawable(singleBg[2]))
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
        if (customWatchfaceData == null) { // if neither CWF nor Default CWF is found, then force reload of default Layout
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
                    // A different zip means the dial looks like something else, which the style
                    // schema the system caches its preview from does not say. Asked for inside this
                    // block, which only runs on a real change, since the system rate limits requests.
                    requestPreviewImageUpdate()
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
                // With the other CWF-wide values: `complicationStyle` is a transversal json section
                // like `metadata` or `dynPref`. Per-slot keys are read in customizeComplicationView.
                complicationStyle.init(json.optJSONObject(JsonKeys.COMPLICATIONSTYLE.key))
                enableExt1 = false
                enableExt2 = false
                binding.mainLayout.forEach { view ->
                    ViewMap.fromId(view.id)?.let { viewMap ->
                        when (view) {
                            is TextView                                 -> viewMap.customizeTextView(view, this)
                            is ImageView                                -> viewMap.customizeImageView(view, this)
                            is lecho.lib.hellocharts.view.LineChartView -> viewMap.customizeGraphView(view, this)
                            is FrameLayout                              -> viewMap.customizeComplicationView(view, this)
                            else                                        -> viewMap.customizeViewCommon(view, this)
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
                    metadata[pref.metadataKey]?.toBooleanStrictOrNull()?.let { sp.putBoolean(pref.prefKey, it) }
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
            .put(JsonKeys.COMPLICATIONSTYLE.key, JSONObject())

        binding.mainLayout.forEach { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            ViewMap.fromId(view.id)?.let {
                if (it.external == 0 || externalViews) {
                    when (view) {
                        is TextView                                 ->
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

                        // Complication placeholders (FrameLayout) get position/size/visibility here
                        // like any other view.
                        is ImageView, is FrameLayout, is lecho.lib.hellocharts.view.LineChartView ->
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
        json.put(JsonKeys.DYNPREF.key, JSONObject())
        json.put(JsonKeys.DYNDATA.key, JSONObject())
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

    // Matched on the part before '#' so a BG colour key can carry an alpha suffix - see
    // withDeclaredAlpha. A plain hex colour starts with '#', so its prefix is empty and never matches.
    private fun getColor(color: String, defaultColor: Int = Color.GRAY): Int =
        when (color.substringBefore('#')) {
            JsonKeyValues.BGCOLOR.key      -> bgColor(0).withDeclaredAlpha(color)
            JsonKeyValues.BGCOLOR_EXT1.key -> bgColor(1).withDeclaredAlpha(color)
            JsonKeyValues.BGCOLOR_EXT2.key -> bgColor(2).withDeclaredAlpha(color)
            else                           ->
                try {
                    color.toColorInt()
                } catch (_: Exception) {
                    defaultColor
                }
        }

    /**
     * This BG colour with the alpha a CWF appended to the key, or unchanged when it appended none:
     * `bgColor#80` is the BG colour at 50% opacity, plain `bgColor` keeps its own opacity.
     *
     * The alpha replaces rather than scales. A suffix that is not exactly two hex digits is ignored
     * rather than refused, like [getColor]'s own `catch`.
     */
    private fun Int.withDeclaredAlpha(color: String): Int =
        color.substringAfter('#', "").takeIf { it.length == 2 }?.toIntOrNull(16)
            ?.let { ColorUtils.setAlphaComponent(this, it) } ?: this

    private fun manageSpecificViews() {
        resolveBackgroundDrawable()
        updateSecondVisibility()
        setSecond() // Update second visibility for time view
        binding.timePeriod.visibility = (binding.timePeriod.isVisible && DateFormat.is24HourFormat(this).not()).toVisibility()
    }

    // background is drawn on the Canvas by onDraw() from a View kept outside mainLayout, so it paints
    // before complications without disturbing mainLayout's own draw order - see [backgroundView].
    // Resolved in the same setWatchfaceStyle() pass as everything else, so its BG-level-dependent
    // image never lags a tick behind the rest of the dial; onDraw() just paints the last result.
    private fun resolveBackgroundDrawable() {
        // Styled by the same call as every other image view, not a second implementation of it.
        ViewMap.BACKGROUND.customizeImageView(backgroundView, this)
        // Then forced to fill the dial and laid out by hand, since it has no parent to measure it. A
        // CWF must not be able to shrink or hide the layer that stops the previous frame ghosting.
        val size = (TEMPLE_RESOLUTION * zoomFactor).toInt()
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        backgroundView.measure(spec, spec)
        backgroundView.layout(0, 0, size, size)
    }

    /**
     * Identity of each complication slot this watch face hosts: the [id] the framework knows it by and
     * the [preferenceKey] of the settings row that opens its data source picker. [ViewMap] points at
     * an entry instead of repeating an id, and the settings screens get the list through
     * [complicationSlots] instead of hardcoding rows of their own.
     *
     * **The ids are a stable public contract** - the system persists the chosen data source per (watch
     * face component, slot id) - so they must never change once shipped, and never be derived from an
     * enum ordinal.
     *
     * Identity only. Live per-slot state (a `ComplicationDrawable` and its invalidate callback) must
     * not live on an enum: entries are process-wide singletons while the editor runs a second, headless
     * CustomWatchface, so the two would share one drawable. That state stays in `ComplicationSlotState`.
     */
    private enum class ComplicationMap(
        override val id: Int,
        @StringRes override val preferenceKey: Int,
        @StringRes override val preferenceTitle: Int,
        /** The "Show Complication N" toggle that decides whether this slot is drawn at all. */
        val showPref: PrefMap,
        /**
         * Key of this slot's own [ComplicationTypePriority]. Separate from [preferenceKey], which
         * chooses *what* the slot shows, where this chooses *which kind of content* is asked for first.
         */
        @StringRes val typePriorityPrefKey: Int
    ) : ComplicationSlotInfo {

        COMPLICATION1(101, R.string.key_complication_1, R.string.pref_complication_1, PrefMap.SHOW_COMPLICATION_1, R.string.key_complication_type_priority_1),
        COMPLICATION2(102, R.string.key_complication_2, R.string.pref_complication_2, PrefMap.SHOW_COMPLICATION_2, R.string.key_complication_type_priority_2),
        COMPLICATION3(103, R.string.key_complication_3, R.string.pref_complication_3, PrefMap.SHOW_COMPLICATION_3, R.string.key_complication_type_priority_3),
        COMPLICATION4(104, R.string.key_complication_4, R.string.pref_complication_4, PrefMap.SHOW_COMPLICATION_4, R.string.key_complication_type_priority_4),
        COMPLICATION5(105, R.string.key_complication_5, R.string.pref_complication_5, PrefMap.SHOW_COMPLICATION_5, R.string.key_complication_type_priority_5)
    }

    private enum class ViewMap(
        val key: String,
        @IdRes val id: Int,
        val pref: PrefMap? = null,                  // PreMap key should be typeBool == true to manage visibility of associated View
        @IdRes val defaultDrawable: Int? = null,
        val customDrawable: ResFileMap? = null,
        val customHigh: ResFileMap? = null,
        val customLow: ResFileMap? = null,
        val external: Int = 0,
        /**
         * The complication slot this entry carries the geometry for, or null for every ordinary view.
         * Non-null marks the entry as a complication placeholder. [WatchFaceComplications] gets its
         * slot list from [ComplicationMap], so a new slot is one entry there plus one here.
         */
        val complication: ComplicationMap? = null
    ) {

        BACKGROUND(
            key = ViewKeys.BACKGROUND.key,
            // NO_ID because its View is not in mainLayout, so the loop that customizes views by id
            // must not find it - resolveBackgroundDrawable() styles it instead.
            id = View.NO_ID,
            defaultDrawable = R.drawable.background,
            customDrawable = ResFileMap.BACKGROUND,
            customHigh = ResFileMap.BACKGROUND_HIGH,
            customLow = ResFileMap.BACKGROUND_LOW
        ),
        // Plain FrameLayout placeholders carrying position/size/visibility/rotation for the real
        // ComplicationSlots (see customizeComplicationView). They draw nothing themselves - the
        // framework renders the complication onto the Canvas. Slot identity lives in ComplicationMap.
        COMPLICATION1(ViewKeys.COMPLICATION1.key, R.id.complication1, complication = ComplicationMap.COMPLICATION1),
        COMPLICATION2(ViewKeys.COMPLICATION2.key, R.id.complication2, complication = ComplicationMap.COMPLICATION2),
        COMPLICATION3(ViewKeys.COMPLICATION3.key, R.id.complication3, complication = ComplicationMap.COMPLICATION3),
        COMPLICATION4(ViewKeys.COMPLICATION4.key, R.id.complication4, complication = ComplicationMap.COMPLICATION4),
        COMPLICATION5(ViewKeys.COMPLICATION5.key, R.id.complication5, complication = ComplicationMap.COMPLICATION5),
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
            fun init(cwf: CustomWatchface) = entries.forEach { view ->
                // load all customized drawable when new watchface is loaded
                view.rangeCustom = view.customDrawable?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources) }
                view.highCustom = view.customHigh?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources) }
                view.lowCustom = view.customLow?.let { cd -> cwf.resDataMap[cd.fileName]?.toDrawable(cwf.resources) }
                view.textDrawable = null // textDrawable not initialized here because it requires update of width and height first
                view.viewJson = cwf.json.optJSONObject(view.key)
                view.twinView = view.viewJson?.let { viewJson -> ViewMap.fromKey(viewJson.optString(JsonKeys.TWINVIEW.key)) }
            }

            fun fromId(id: Int): ViewMap? = entries.firstOrNull { it.id == id }
            fun fromKey(key: String?): ViewMap? = entries.firstOrNull { it.key == key }

            /**
             * Ring thickness in physical pixels for `RANGED_VALUE`/`GOAL_PROGRESS`, so a CWF can declare
             * it instead of getting the library's fixed hairline. Pure arithmetic - the json reads that
             * feed it happen in [customizeComplicationView], like every other key in that block.
             *
             * One unit convention: [ringWidth], [PROVISIONAL_RING_WIDTH] and [declaredWidth] are all
             * ints in the CWF's 400x400 space, so all take [zoomFactor] and nothing else, the same
             * conversion [customizeViewCommon] applies to every other view.
             * `ComplicationStyle.rangedValueRingWidth` is `@Px`, so the result is already in the right
             * unit. [ComplicationStyleValues.MIN_RING_WIDTH_PX] is in that unit too and is **not**
             * scaled: an absolute floor, not a design value.
             *
             * Do not switch the default to the library's `2dp`: `dp` is Android's own device conversion
             * and multiplying it by [zoomFactor] would apply two unrelated scaling schemes, while using
             * it unscaled would make ring width the one CWF dimension that does not scale with its slot.
             *
             * Rounds rather than truncates, matching `ComplicationSlot.computeBounds`.
             */
            fun ringWidthPx(declaredWidth: Int, ringWidth: Int?, zoomFactor: Double): Int {
                val slotWidthPx = declaredWidth * zoomFactor
                // Declared or defaulted, both are ints in the same 400x400 space as WIDTH/HEIGHT.
                val raw = (ringWidth ?: PROVISIONAL_RING_WIDTH) * zoomFactor
                // The ring is stroked centred on its arc, so anything wider than the slot would paint
                // outside the complication.
                val capped = if (slotWidthPx > 0) raw.coerceAtMost(slotWidthPx) else raw
                return (0.5 + capped).toInt().coerceAtLeast(ComplicationStyleValues.MIN_RING_WIDTH_PX)
            }
        }

        var width = 0
        var height = 0
        var left = 0
        var top = 0
        var viewJson: JSONObject? = null
        fun visibility(cwf: CustomWatchface): Int = viewJson?.let { cwf.setVisibility(it.optString(JsonKeys.VISIBILITY.key, JsonKeyValues.GONE.key), prefVisibility(cwf)) } ?: View.GONE
        var dynData: DynProvider? = null
        var rangeCustom: Drawable? = null
        var highCustom: Drawable? = null
        var lowCustom: Drawable? = null
        var textDrawable: Drawable? = null
        fun drawable(cwf: CustomWatchface): Drawable? = dynData?.getDrawable(cwf) ?: when (cwf.singleBg[0].sgvLevel) {
            1L   -> highCustom ?: rangeCustom
            0L   -> rangeCustom
            -1L  -> lowCustom ?: rangeCustom
            else -> rangeCustom
        }
        var twinView: ViewMap? = null
            get() = field ?: viewJson?.let { viewJson -> ViewMap.fromKey(viewJson.optString(JsonKeys.TWINVIEW.key)).also { twinView = it } }

        /**
         * The preference that gates this view: its own [pref], or, for a complication placeholder, the
         * one its slot carries - so slot id and "Show Complication N" stay together in [ComplicationMap].
         */
        val visibilityPref: PrefMap? get() = pref ?: complication?.showPref

        fun prefVisibility(cwf: CustomWatchface): Boolean = visibilityPref?.let { cwf.sp.getBoolean(it.prefKey, it.defaultValue as Boolean) } != false

        fun textDrawable(cwf: CustomWatchface): Drawable? = textDrawable
            ?: cwf.resDataMap[viewJson?.optString(JsonKeys.BACKGROUND.key)]?.toDrawable(cwf.resources, width, height)?.also { textDrawable = it }

        fun customizeViewCommon(view: View, cwf: CustomWatchface) {
            view.visibility = visibility(cwf)
            viewJson?.let { viewJson ->
                width = (viewJson.optInt(JsonKeys.WIDTH.key) * cwf.zoomFactor).toInt()
                height = (viewJson.optInt(JsonKeys.HEIGHT.key) * cwf.zoomFactor).toInt()
                left = (viewJson.optInt(JsonKeys.LEFTMARGIN.key) * cwf.zoomFactor).toInt()
                top = (viewJson.optInt(JsonKeys.TOPMARGIN.key) * cwf.zoomFactor).toInt()
                val params = FrameLayout.LayoutParams(width, height)
                dynData = DynProvider.getDyn(cwf, viewJson.optString(JsonKeys.DYNPREF.key), viewJson.optString(JsonKeys.DYNDATA.key), width, height, key)
                val topOffset = ((if (viewJson.optBoolean(JsonKeys.TOPOFFSET.key, false)) dynData?.getTopOffset(cwf) ?: 0 else 0) * cwf.zoomFactor).toInt()
                val topOffsetTwin = ((twinView?.let { if (it.visibility(cwf) != View.VISIBLE) viewJson.optInt(JsonKeys.TOPOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val topOffsetStep = ((dynData?.getTopOffsetStep(cwf) ?: 0) * cwf.zoomFactor).toInt()
                params.topMargin = top + topOffset + topOffsetTwin + topOffsetStep
                val leftOffset = ((if (viewJson.optBoolean(JsonKeys.LEFTOFFSET.key, false)) dynData?.getLeftOffset(cwf) ?: 0 else 0) * cwf.zoomFactor).toInt()
                val leftOffsetTwin = ((twinView?.let { if (it.visibility(cwf) != View.VISIBLE) viewJson.optInt(JsonKeys.LEFTOFFSETTWINHIDDEN.key, 0) else 0 } ?: 0) * cwf.zoomFactor).toInt()
                val leftOffsetStep = ((dynData?.getLeftOffsetStep(cwf) ?: 0) * cwf.zoomFactor).toInt()
                params.leftMargin = left + leftOffset + leftOffsetTwin + leftOffsetStep
                view.layoutParams = params
                val rotationOffset = if (viewJson.optBoolean(JsonKeys.ROTATIONOFFSET.key, false)) dynData?.getRotationOffset(cwf)?.toFloat() ?: 0F else 0F
                val rotationOffsetStep = (dynData?.getRotationOffsetStep(cwf) ?: 0).toFloat()
                view.rotation = viewJson.optInt(JsonKeys.ROTATION.key).toFloat() + rotationOffset + rotationOffsetStep
            }
        }

        /**
         * The complication-placeholder counterpart of [customizeTextView] and friends.
         *
         * Position/size/visibility/rotation are not applied to the complication itself here -
         * [customizeViewCommon] writes them onto the placeholder view and
         * `CustomWatchface.complicationRender` reads them back per frame. What is left is the styling
         * `ComplicationDrawable` accepts at any time.
         *
         * Every key of the per-slot json block is read here off [viewJson], like [customizeTextView]
         * reads its own. The one difference is where each chain ends: at the CWF-wide
         * `complicationStyle` section rather than at a literal.
         */
        fun customizeComplicationView(view: FrameLayout, cwf: CustomWatchface) {
            // Called first and unconditionally, and nothing below may short-circuit it: it gives a
            // complication every geometry behaviour an ordinary view has, and builds dynData, which
            // the reads below depend on.
            customizeViewCommon(view, cwf)
            val state = complication?.let { cwf.complications.stateFor(it.id) } ?: return
            val global = cwf.complicationStyle
            // Each chain: this slot's dynData step (only where DynProvider exposes one), then this
            // slot's own json key, then the CWF-wide complicationStyle value. dynData is the instance
            // customizeViewCommon just built from this block's DYNPREF/DYNDATA.
            val textColor = dynData?.getFontColorStep(cwf)
                ?: viewJson?.optString(JsonKeys.FONTCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.fontColor) }
                ?: global.fontColor
            // No getTitleColorStep/getIconColorStep on DynProvider, so these two start at the json.
            val titleColor = viewJson?.optString(JsonKeys.FONTTITLECOLOR.key)?.takeIf { it.isNotEmpty() }
                ?.let { cwf.getColor(it, global.titleColor) } ?: global.titleColor
            // ICONCOLOR wins; without it the icon follows this slot's own text colour; only if neither
            // is declared does the global value apply.
            val iconColor = viewJson?.optString(JsonKeys.ICONCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.iconColor) }
                ?: viewJson?.optString(JsonKeys.FONTCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.iconColor) }
                ?: global.iconColor
            // `has` rather than the emptiness test used above: a COLOR key present but empty resolves
            // through getColor's own default instead of falling back. COLOR alone behaves this way.
            val backgroundColor = dynData?.getColorStep(cwf)
                ?: viewJson?.let { json -> if (json.has(JsonKeys.COLOR.key)) cwf.getColor(json.optString(JsonKeys.COLOR.key)) else null }
                ?: global.backgroundColor
            val textTypeface = typefaceOf(viewJson, JsonKeys.FONT.key, JsonKeys.FONTSTYLE.key, global.font)
            val titleTypeface = typefaceOf(viewJson, JsonKeys.FONTTITLE.key, JsonKeys.TITLESTYLE.key, global.titleFont)
            // Sizes are @Px upper bounds - TextRenderer shrinks to fit - so they take the same
            // zoomFactor scaling as every other CWF dimension. The global values arrive already scaled.
            val textSize = (dynData?.getTextSizeStep(cwf) ?: viewJson?.optInt(JsonKeys.TEXTSIZE.key)?.takeIf { it > 0 })
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.textSize
            val titleSize = viewJson?.optInt(JsonKeys.TITLESIZE.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.titleSize
            val borderRadius = viewJson?.optInt(JsonKeys.BORDERRADIUS.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.borderRadius
            val borderColor = viewJson?.optString(JsonKeys.BORDERCOLOR.key)?.takeIf { it.isNotEmpty() }
                ?.let { cwf.getColor(it, Color.TRANSPARENT) } ?: global.borderColor
            val borderWidth = viewJson?.optInt(JsonKeys.BORDERWIDTH.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.borderWidth
            val ringWidth = ringWidthPx(
                viewJson?.optInt(JsonKeys.WIDTH.key) ?: 0,
                viewJson?.optInt(JsonKeys.RINGWIDTH.key)?.takeIf { it > 0 } ?: global.ringWidth,
                cwf.zoomFactor
            )
            val ringPrimaryColor = viewJson?.optString(JsonKeys.RINGPRIMARYCOLOR.key)?.takeIf { it.isNotEmpty() }
                ?.let { cwf.getColor(it) } ?: global.ringPrimaryColor
            val ringSecondaryColor = viewJson?.optString(JsonKeys.RINGSECONDARYCOLOR.key)?.takeIf { it.isNotEmpty() }
                ?.let { cwf.getColor(it) } ?: global.ringSecondaryColor
            state.style = ComplicationStyleValues(
                textColor = textColor,
                titleColor = titleColor,
                iconColor = iconColor,
                backgroundColor = backgroundColor,
                textTypeface = textTypeface,
                titleTypeface = titleTypeface,
                borderRadius = borderRadius,
                ringWidth = ringWidth,
                textSize = textSize,
                titleSize = titleSize,
                ringPrimaryColor = ringPrimaryColor,
                ringSecondaryColor = ringSecondaryColor,
                borderColor = borderColor,
                borderWidth = borderWidth
            )
            state.applyStyle()
            // Not part of the style values: nothing in ComplicationDrawable holds it, our renderer
            // reads it per frame. Same cascade - this slot, the CWF-wide block, then the library.
            state.imageFit = ImageFitMap.fit(viewJson?.optString(JsonKeys.IMAGEFIT.key), global.imageFit)
            // Only an ICONCOLOR actually written by the CWF counts, here or CWF-wide - not the
            // FONTCOLOR that iconColor falls back to. This flag is what allows a provider's untintable
            // small image to be dropped for the tintable icon, and a text colour never asked for that.
            state.iconColorRequested = viewJson?.optString(JsonKeys.ICONCOLOR.key)?.isNotEmpty() == true || global.iconColorRequested
        }

        fun customizeTextView(view: TextView, cwf: CustomWatchface) {
            customizeViewCommon(view, cwf)
            viewJson?.let { viewJson ->
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ((dynData?.getTextSizeStep(cwf) ?: viewJson.optInt(JsonKeys.TEXTSIZE.key, 22)) * cwf.zoomFactor).toFloat())
                view.gravity = GravityMap.gravity(viewJson.optString(JsonKeys.GRAVITY.key, GravityMap.CENTER.key))
                view.setTypeface(
                    FontMap.font(viewJson.optString(JsonKeys.FONT.key, FontMap.DEFAULT.key)),
                    StyleMap.style(viewJson.optString(JsonKeys.FONTSTYLE.key, StyleMap.NORMAL.key))
                )
                view.setTextColor(dynData?.getFontColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.FONTCOLOR.key)))
                view.isAllCaps = viewJson.optBoolean(JsonKeys.ALLCAPS.key)
                if (viewJson.has(JsonKeys.TEXTVALUE.key) || viewJson.has(JsonKeys.DYNVALUE.key) || (dynData?.stepTextValue ?: 0) > 0) {
                    if (viewJson.has(JsonKeys.DYNVALUE.key)) {
                        dynData?.getDynValue(viewJson.optBoolean(JsonKeys.DYNVALUE.key, false), cwf)?.let {
                            try {   // try - catch block if wrong format provided not consistent with double values, to avoid crash
                                view.text = String.format(cwf.context.resources.configuration.locales[0], dynData?.getTextValueStep(cwf) ?: viewJson.optString(JsonKeys.TEXTVALUE.key, "%.0f"), it)
                            } catch (_: Exception) {
                                view.text = String.format(cwf.context.resources.configuration.locales[0], "%.0f", it)
                            }
                        } ?: apply {
                            view.text = dynData?.getTextValueStep(cwf) ?: viewJson.optString(JsonKeys.TEXTVALUE.key)
                        }
                    } else {
                        view.text = dynData?.getTextValueStep(cwf) ?: viewJson.optString(JsonKeys.TEXTVALUE.key)
                    }
                }
                (dynData?.getDrawable(cwf) ?: textDrawable(cwf))?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)           // Note only works on bitmap (png or jpg)  not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                    // if no drawable loaded either background key or dynData, then apply color to text background
                    view.setBackgroundColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
                }
            } ?: apply { view.text = "" }
        }

        fun customizeImageView(view: ImageView, cwf: CustomWatchface) {
            customizeViewCommon(view, cwf)
            view.clearColorFilter()
            viewJson?.let { viewJson ->
                drawable(cwf)?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.setImageDrawable(it)
                } ?: apply {
                    view.setImageDrawable(defaultDrawable?.let { ResourcesCompat.getDrawable(cwf.resources, it, cwf.theme) })
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)       // works on XML included into res files
                        view.setColorFilter(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        view.clearColorFilter()
                }
                if (view.drawable == null)                                              // if no drawable (either default, hardcoded or dynData, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
            }
        }

        fun customizeGraphView(view: lecho.lib.hellocharts.view.LineChartView, cwf: CustomWatchface) {
            customizeViewCommon(view, cwf)
            viewJson?.let { viewJson ->
                (dynData?.getDrawable(cwf) ?: textDrawable(cwf))?.let {
                    if (viewJson.has(JsonKeys.COLOR.key) || (dynData?.stepColor ?: 0) > 0)        // Note only works on bitmap (png or jpg) not for svg files
                        it.colorFilter = cwf.changeDrawableColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key)))
                    else
                        it.clearColorFilter()
                    view.background = it
                } ?: apply {                                                // if no drawable loaded, then apply color to background
                    view.setBackgroundColor(dynData?.getColorStep(cwf) ?: cwf.getColor(viewJson.optString(JsonKeys.COLOR.key, TRANSPARENT), Color.TRANSPARENT))
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

            fun init(cwf: CustomWatchface) = entries.forEach { trendArrow ->
                trendArrow.arrowCustom = trendArrow.customDrawable?.let { cwf.resDataMap[it.fileName]?.toDrawable(cwf.resources) } ?: ResourcesCompat.getDrawable(cwf.resources, trendArrow.icon, cwf.theme)
            }

            fun drawable(singleBg: EventData.SingleBg) = entries.firstOrNull { it.symbol == singleBg.slopeArrow }?.arrowCustom ?: NONE.arrowCustom
            fun value(singleBg: EventData.SingleBg) = entries.firstOrNull { it.symbol == singleBg.slopeArrow }?.dynValue ?: NONE.dynValue
        }

        var arrowCustom: Drawable? = null
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

    private enum class FontMap(val key: String, var font: Typeface, @FontRes val fontResources: Int? = null) {
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
                    customFonts[fontMap.key.lowercase()] = fontMap.fontResources?.let { fontResource ->
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

    /**
     * The CWF json vocabulary for [ComplicationImageFit] - the `imageFit` key of a complication block
     * or of the CWF-wide `complicationStyle` block.
     */
    private enum class ImageFitMap(val key: String, val fit: ComplicationImageFit) {
        FIT_CENTER(JsonKeyValues.FIT_CENTER.key, ComplicationImageFit.FIT_CENTER),
        CENTER_CROP(JsonKeyValues.CENTER_CROP.key, ComplicationImageFit.CENTER_CROP),
        FIT_XY(JsonKeyValues.FIT_XY.key, ComplicationImageFit.FIT_XY);

        companion object {

            /** [default] for an absent, empty or unknown value, so a typo never changes the layout. */
            fun fit(key: String?, default: ComplicationImageFit) =
                key?.takeIf { it.isNotEmpty() }?.let { value -> entries.firstOrNull { it.key == value }?.fit } ?: default
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

    // This class contains mapping between keys used within JSON of Custom Watchface and preferences
    // Note defaultValue should be identical to default value in XML file,
    // defaultValue Type set to Any for future updates (Boolean or String) (key_digital_style_frame_style, key_digital_style_frame_color are strings...)
    private enum class PrefMap(
        val key: String,
        @StringRes val prefKey: Int,
        val defaultValue: Any,
        val typeBool: Boolean,
        /**
         * Row label for the few preferences [CwfMetadataKey] does not name - see [title], which is
         * where every other one gets its label from. Null also means "no row at all": [PREF_UNITS]
         * is pushed from the phone, never chosen on the watch.
         */
        @StringRes private val localTitle: Int? = null
    ) {

        SHOW_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_IOB.key, R.string.key_show_iob, true, true),
        SHOW_COMPLICATION_1(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION1.key, R.string.key_show_complication_1, false, true),
        SHOW_COMPLICATION_2(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION2.key, R.string.key_show_complication_2, false, true),
        SHOW_COMPLICATION_3(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION3.key, R.string.key_show_complication_3, false, true),
        SHOW_COMPLICATION_4(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION4.key, R.string.key_show_complication_4, false, true),
        SHOW_COMPLICATION_5(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION5.key, R.string.key_show_complication_5, false, true),
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
        PREF_DARK(JsonKeyValues.PREF_DARK.key, R.string.key_dark, true, true, R.string.pref_dark),
        PREF_MATCH_DIVIDER(JsonKeyValues.PREF_MATCH_DIVIDER.key, R.string.key_match_divider, false, true, R.string.pref_matching_divider);

        /**
         * The CWF metadata entry that names this preference, or null for the few watch preferences a
         * zip cannot carry in its metadata - those are named by [JsonKeyValues] instead.
         */
        val metadataKey: CwfMetadataKey? get() = CwfMetadataKey.fromKey(key)

        /**
         * The label of this preference's row on the settings screen, or null when it has no row. Taken
         * from [metadataKey] wherever there is one, so a preference is labelled by the same string the
         * phone shows for it. See `CustomWatchfaceConfigurationFragment`, which builds the screen.
         */
        @get:StringRes val title: Int? get() = metadataKey?.label ?: localTitle

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

        fun dataValue(cwf: CustomWatchface): Double? = when (valueMap) {
            ValueMap.NONE             -> 0.0
            ValueMap.SGV              -> if (cwf.singleBg[0].sgvString != "---") cwf.singleBg[0].sgv else null
            ValueMap.SGV_LEVEL        -> if (cwf.singleBg[0].sgvString != "---") cwf.singleBg[0].sgvLevel.toDouble() else null
            ValueMap.DIRECTION        -> TrendArrowMap.value(cwf.singleBg[0])
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
            ValueMap.DIRECTION_EXT1   -> TrendArrowMap.value(cwf.singleBg[1])
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
            ValueMap.DIRECTION_EXT2   -> TrendArrowMap.value(cwf.singleBg[2])
            ValueMap.DELTA_EXT2       -> cwf.singleBg[2].deltaMgdl
            ValueMap.AVG_DELTA_EXT2   -> cwf.singleBg[2].avgDeltaMgdl
            ValueMap.TEMP_TARGET_EXT2 -> cwf.status[2].tempTargetLevel.toDouble()
            ValueMap.RESERVOIR_EXT2   -> cwf.status[2].reservoir
            ValueMap.RESERVOIR_LEVEL_EXT2 -> cwf.status[2].reservoirLevel.toDouble()
            ValueMap.RIG_BATTERY_EXT2 -> cwf.status[2].rigBattery.replace("%", "").toDoubleOrNull()
            ValueMap.LOOP_EXT2        -> if (cwf.status[2].openApsStatus != -1L) ((System.currentTimeMillis() - cwf.status[2].openApsStatus) / 1000 / 60).toDouble() else null
            ValueMap.TIMESTAMP_EXT2   -> if (cwf.singleBg[2].timeStamp != 0L) floor(cwf.timeSince(2) / (1000 * 60)) else null
        }

        fun getTopOffset(cwf: CustomWatchface): Int = dataRange?.let { dataRange ->
            topRange?.let { topRange ->
                dataValue(cwf)?.let { (valueMap.dynValue(it, dataRange, topRange) * cwf.zoomFactor).toInt() }
                    ?: (topRange.invalidData * cwf.zoomFactor).toInt()
            }
        } ?: 0

        fun getLeftOffset(cwf: CustomWatchface): Int = dataRange?.let { dataRange ->
            leftRange?.let { leftRange ->
                dataValue(cwf)?.let { (valueMap.dynValue(it, dataRange, leftRange) * cwf.zoomFactor).toInt() }
                    ?: (leftRange.invalidData * cwf.zoomFactor).toInt()
            }
        } ?: 0

        fun getRotationOffset(cwf: CustomWatchface): Int = dataRange?.let { dataRange -> rotationRange?.let { rotRange -> dataValue(cwf)?.let { valueMap.dynValue(it, dataRange, rotRange).toInt() } ?: rotRange.invalidData } } ?: 0
        fun getDrawable(cwf: CustomWatchface) = dataRange?.let { dataRange -> dataValue(cwf)?.let { dynDrawable[valueMap.stepValue(it, dataRange, stepDraw)] } ?: dynDrawable[0] }
        fun getFontColorStep(cwf: CustomWatchface) = getIntValue(dynFontColor, stepFontColor, cwf)
        fun getTextSizeStep(cwf: CustomWatchface) = getIntValue(dynTextSize, stepTextSize, cwf)
        fun getColorStep(cwf: CustomWatchface) = getIntValue(dynColor, stepColor, cwf)
        fun getTopOffsetStep(cwf: CustomWatchface) = getIntValue(dynTopOffset, stepTopOffset, cwf)
        fun getLeftOffsetStep(cwf: CustomWatchface) = getIntValue(dynLeftOffset, stepLeftOffset, cwf)
        fun getRotationOffsetStep(cwf: CustomWatchface) = getIntValue(dynRotationOffset, stepRotationOffset, cwf)
        fun getTextValueStep(cwf: CustomWatchface) = dataRange?.let { dataRange -> dataValue(cwf)?.let { dynTextValue[valueMap.stepValue(it, dataRange, stepTextValue)] } ?: dynTextValue[0] }
        fun getDynValue(range: Boolean, cwf: CustomWatchface): Double? =
            if (range)
                dataRange?.let { dataRange ->
                    dynRange?.let { dynRange ->
                        dataValue(cwf)?.let { (valueMap.dynValue(it, dataRange, dynRange)) }
                            ?: (dynRange.invalidData.toDouble())
                    }
                } ?: dataValue(cwf)
            else
                dataValue(cwf)

        private fun getIntValue(dynMap: MutableMap<Int, Int>, step: Int, cwf: CustomWatchface) =
            if (step > 0) dataRange?.let { dataRange -> dataValue(cwf)?.let { dynMap[valueMap.stepValue(it, dataRange, step)] } ?: dynMap[0] ?: dynMap[1] } else null

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

    // block below build a map of prefKey => JSON Bloc recursively
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