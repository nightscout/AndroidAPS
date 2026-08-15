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
     * A real [ImageView] for background, deliberately not part of `activity_custom.xml`.
     *
     * background must paint before complications, and everything in that layout paints after them
     * (see [deferMainLayoutDraw]), so it cannot be a child there. Keeping a View anyway - just an
     * unattached one, measured and laid out by hand in [resolveBackgroundDrawable] and drawn onto the
     * Canvas in [onDraw] - means background is styled by the very same [ViewMap.customizeImageView]
     * every other image goes through. Not a copy of it: the copy this replaced had already drifted,
     * silently losing colour steps and the built-in drawable fallback.
     *
     * Per engine instance, never enum state: the editor runs a second, headless CustomWatchface.
     */
    private val backgroundView by lazy { ImageView(this) }

    // Order is a real preference order, not cosmetic: during data source selection the system walks
    // this list in order and picks the first entry a provider also supports (ComplicationSlot.kt's
    // supportedTypes doc, confirmed against source - see _docs/Complication_Libraries.md's
    // "Supported-type negotiation" section). Data-bearing types are listed first so a provider
    // capable of sending a live value (e.g. heart rate as RANGED_VALUE) is asked for that instead of
    // defaulting to a static SHORT_TEXT label - which is what a Samsung Health heart-rate provider
    // was confirmed (via device logcat) to send when SHORT_TEXT was first in this list.
    // GOAL_PROGRESS/WEIGHTED_ELEMENTS are @RequiresApi(TIRAMISU) on the ComplicationType enum
    // constants themselves - gated rather than included unconditionally despite that annotation
    // being lint-only (referencing the constant can't itself crash on an older device), because
    // whether a pre-Tiramisu Wear OS system service understands that wire type at all is a
    // separate, unverified question this project has no way to test on such a device.
    //
    // RANGED_VALUE deliberately outranks GOAL_PROGRESS despite the latter's more promising name.
    // ComplicationRenderer.drawGoalProgress() renders GOAL_PROGRESS on a completely separate path
    // from every other type, with visuals we cannot style: progress is scaled against
    // targetValue * 1.1 (so it always reads ~9% low), the progress mark is a dot rather than a
    // filled arc, and a 36-degree arc at the end of the circle is painted with a hardcoded
    // Color.RED that no ComplicationStyle property can override. RANGED_VALUE uses the ordinary
    // arc renderer and the same content layout, so it is the better target for a
    // progress-toward-a-goal complication. Confirmed on device against a provider offering both.
    //
    // TODO(later): a `SUPPORTEDTYPES` CWF key would replace this list. Creation-time only - it is a
    //  ComplicationSlot.Builder argument, so a value arriving later cannot be applied.
    private val complicationSupportedTypes =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.GOAL_PROGRESS,
                ComplicationType.WEIGHTED_ELEMENTS,
                ComplicationType.SHORT_TEXT,
                ComplicationType.LONG_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE,
                ComplicationType.PHOTO_IMAGE
            )
        } else {
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.SHORT_TEXT,
                ComplicationType.LONG_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE,
                ComplicationType.PHOTO_IMAGE
            )
        }

    /**
     * The generic complication plumbing for *this* engine instance - see [WatchFaceComplications],
     * which holds everything that is not specific to this watch face.
     *
     * The slot list comes straight from [ComplicationMap], so adding a slot means one new entry
     * there (plus the [ViewMap] entry carrying its geometry) and nothing here.
     *
     * Deliberately an instance field rather than enum state. Complication state includes live objects
     * bound to this one engine (a `ComplicationDrawable` owning a renderer and an invalidate
     * callback), and the watch face editor runs a **second, headless** CustomWatchface instance
     * concurrently. Enum entries - the pattern [ViewMap] and [ComplicationMap] use - are process-wide
     * singletons and could not hold that safely: the two instances would share one drawable. Which is
     * why [ComplicationMap] carries slot *identity* only, and never a drawable.
     */
    private val complications = WatchFaceComplications(
        context = this,
        slotIds = complicationSlots.map { it.id },
        supportedTypes = complicationSupportedTypes,
        layoutSettingLabel = R.string.cwf_complication_layout,
        layoutSettingDescription = R.string.cwf_complication_layout_description
    )

    /**
     * The CWF-wide `complicationStyle` values, decoded once per [setWatchfaceStyle] pass alongside
     * every other transversal value - see [ComplicationGlobalStyle].
     */
    private val complicationStyle = ComplicationGlobalStyle(this)

    // internal, not public: [complicationSlots] hands out an internal type, and nothing outside this
    // module has any business with this watch face's internals anyway.
    internal companion object : WatchFaceComplicationSlots, WatchFaceSettings {

        /**
         * This watch face's half of the [WatchFaceComplicationSlots] contract: what the settings
         * screens ask for when they need one data source picker per slot.
         *
         * [ComplicationMap] itself is the answer - the entries are already in screen order - so
         * neither the picker nor [WatchFaceComplications] ever sees [ViewMap] or the CWF json.
         */
        override val complicationSlots: List<ComplicationSlotInfo> = ComplicationMap.entries

        /**
         * This watch face's settings screen, built from the enums that already define its behaviour
         * instead of from a settings xml that has to be kept in step with them by hand - the kind of
         * hand-syncing that let two `CwfMetadataKey` entries carry the same json key unnoticed.
         *
         * A `get()` rather than a stored list because it will soon depend on the loaded CWF: deciding
         * that a row is irrelevant to the current watch face is this class's job, not the settings
         * screen's, since only this class may read the CWF json.
         *
         * Rows that are not about what the dial *shows* are listed literally: "include external
         * views" and "simplify UI" drive what the template contains and how data is presented, so
         * they are not tied to a [PrefMap]/[ViewMap] pair the way the toggles above them are.
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
                // Rows about what the dial shows: kept only while the loaded CWF has something for
                // them to act on.
                listOf(PrefMap.SHOW_SECOND, PrefMap.SHOW_WEEK_NUMBER, PrefMap.PREF_DARK, PrefMap.PREF_MATCH_DIVIDER, PrefMap.SHOW_DATE)
                    .filter { it.isUsedBy(json) }
                    .forEach { pref -> pref.toggle()?.let { add(it) } }
                // Which of two patients' data an external view shows - meaningless unless the CWF
                // draws external views at all. Keyed on the views themselves rather than on a
                // PrefMap: no single preference gates them, they are marked by [ViewMap.external].
                if (json == null || ViewMap.entries.any { it.external > 0 && it.isShownBy(json) })
                    add(WatchFaceSettingRow.Toggle(R.string.key_switch_external, R.string.pref_switch_external, false))
                // Each slot the CWF actually draws contributes its pair: the toggle that shows it,
                // then the picker that chooses what it shows, greyed while the toggle is off. A zip
                // with no complication block contributes nothing, which is the common case.
                ComplicationMap.entries.filter { it.showPref.isUsedBy(json) }.forEach { slot ->
                    slot.showPref.toggle()?.let { add(it) }
                    add(WatchFaceSettingRow.Action(slot.preferenceKey, slot.preferenceTitle, dependencyKey = slot.showPref.prefKey))
                }
                // Last, and never filtered: these two are not about what this dial shows. One decides
                // how data is presented, the other what a template exported from here contains - so
                // no CWF can make either irrelevant, and both stay reachable whatever is loaded.
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
         * This preference as an on/off row, so its key, default and label are declared once - null
         * for a preference with no label, which is one with no row at all (see [PrefMap.title]).
         */
        private fun PrefMap.toggle(): WatchFaceSettingRow.Toggle? =
            title?.let { WatchFaceSettingRow.Toggle(prefKey, it, defaultValue as Boolean) }

        /**
         * Whether the loaded CWF gives this preference anything to act on: a view it gates that the
         * zip asks to show, or a `dynPref` block keyed on it.
         *
         * The second arm matters as much as the first. A CWF commonly drives colours from a
         * preference no view names at all - `key_dark` switching every `color1`/`fontColor1` through
         * one dynPref block is the usual way a watch face follows the dark/light setting - and
         * looking only at views would hide exactly the preference such a zip depends on most.
         *
         * No json - nothing loaded, or it could not be parsed - keeps every row: a setting missing
         * because a read failed is worse than one row too many.
         */
        private fun PrefMap.isUsedBy(json: JSONObject?): Boolean {
            json ?: return true
            return ViewMap.entries.any { it.visibilityPref == this && it.isShownBy(json) } ||
                json.optJSONObject(JsonKeys.DYNPREF.key).keysOn(key)
        }

        /**
         * Whether the CWF declares this view and asks for it to be shown.
         *
         * Deliberately reads the json alone, never [ViewMap.prefVisibility]: the preference decides
         * whether the view is drawn, and a row that vanished as soon as the user switched it off
         * could never be switched back on.
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
         * The fixed coordinate space every CWF json declares its geometry in. A format constant
         * rather than per-instance state, so code that has no [CustomWatchface] instance to hand -
         * such as `ComplicationHost.slotBounds` - can reach it.
         */
        private const val TEMPLE_RESOLUTION = 400

        /**
         * A typeface combining a `FONT`-style key with a `FONTSTYLE`-style one, for the complication
         * properties that accept only a single [Typeface] and have no separate style argument.
         *
         * `ComplicationStyle.setTextTypeface`/`setTitleTypeface` take one `Typeface`, unlike
         * `TextView.setTypeface(tf, style)`. `Typeface.create(base, style)` is the equivalent for the
         * system families (`sans-serif`, `serif`, `monospace`, default), which carry real bold and
         * italic variants. **It is not fully equivalent for a single-weight custom font**: `TextView`
         * additionally sets `fakeBoldText`/`textSkewX` on its paint when the family has no matching
         * variant, and `ComplicationStyle` exposes no paint to do that on - so asking for bold on a
         * font resource that ships only one weight yields the unstyled face rather than a synthesised
         * bold.
         */
        private fun typefaceOf(json: JSONObject?, fontKey: String, styleKey: String, default: Typeface): Typeface {
            val base = json?.optString(fontKey)?.takeIf { it.isNotEmpty() }?.let { FontMap.font(it) } ?: default
            val style = json?.optString(styleKey)?.takeIf { it.isNotEmpty() }?.let { StyleMap.style(it) } ?: return base
            return Typeface.create(base, style)
        }


        /**
         * **Provisional placeholder, not a finished value.** Ring thickness as a fraction of the
         * slot's declared width, eyeballed against an OEM watch face (ring ~= 1/6 of radius, and
         * radius = width/2, hence 12). Not an upstream design guideline - no such guideline exists,
         * see [ViewMap.ringWidthPx]. To be replaced by an explicit CWF json key in the
         * styling parameter audit, at which point this becomes only the documented fallback ratio
         * used when the key is absent.
         */
        private const val PROVISIONAL_RING_WIDTH_RATIO = 12


    }

    override fun createUserStyleSchema(): UserStyleSchema = complications.createUserStyleSchema()

    // This method is foundational engine setup and must never throw - confirmed via a real crash
    // (UninitializedPropertyAccessException on complicationDataRepository, logged internally by
    // WatchFaceService's own background-thread handler) that it can run before BaseWatchFace's
    // Dagger injection has completed. It is also the first thing the framework calls on a headless
    // instance, which never gets an onCreate at all, so ensureInjected() runs first: that both
    // makes complicationDataRepository usable here and lets the editor's own headless instance
    // build slots from the real CWF json instead of falling back to default bounds.
    // daggerInjectionComplete is still checked rather than catching UninitializedPropertyAccessException,
    // since a broad catch here would also mask unrelated lateinit bugs in this call chain as
    // "not injected yet"; if injection genuinely could not happen, fall back to each slot's default
    // bounds (same as a fresh install) - a valid manager with the right slot ids beats a crashed one.
    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        ensureInjected()
        // Read here rather than in WatchFaceComplications because ensureInjected /
        // daggerInjectionComplete are protected members of BaseWatchFace, reachable from this subclass
        // but not from another class. The generic layer only needs the resulting bounds.
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
     * other CWF-driven view instead of being frozen at whatever the json said when the engine was
     * created. See [complicationRender] for why rendering can do this while the slot's own bounds
     * cannot be changed.
     *
     * There is nothing to recompute here: [customizeComplicationView] -> `customizeViewCommon`
     * already re-derives the placeholder [FrameLayout]'s size, margins, rotation and visibility from
     * the current json plus [DynProvider] on every refresh, and [BaseWatchFace.onDraw] re-measures
     * and re-lays-out `mainLayout` on every frame before complications are drawn. So reading the
     * laid-out placeholder is what makes a complication inherit *every* dynamic behaviour the other
     * views have - including ones added later - rather than reimplementing a subset of them.
     *
     * Falls back to [ComplicationRender.Declared] whenever there is no usable layout to read (the
     * editor's headless instance never inflates one), which is exactly the pre-hook behaviour.
     */
    override fun complicationRender(slot: ComplicationSlot): ComplicationRender {
        // Stays here: the geometry source is this watch face's own placeholder layout.
        val view = complicationPlaceholder(slot.id) ?: return ComplicationRender.Declared
        if (view.width == 0 || view.height == 0) return ComplicationRender.Declared
        if (!view.isVisible) return ComplicationRender.Skip
        // simpleUi replaces the whole layout (BaseWatchFace.drawMainLayout skips it), so painting
        // complications over it would leave them floating on a screen nothing else is drawn on.
        if (simpleUi.isEnabled(currentWatchMode)) return ComplicationRender.Skip
        return ComplicationRender.At(Rect(view.left, view.top, view.right, view.bottom), view.rotation)
    }

    /**
     * Placeholder views by slot id, resolved once per inflate rather than per lookup.
     *
     * Cached because the lookup is on the render path - `complicationRender` asks per slot per frame
     * and `syncSlotGeometry` once more - so resolving by `findViewById` each time would walk a
     * ~90-view tree several times a frame, where the previous hardcoded `when` was a direct field
     * read. Being empty before inflate also replaces the old `::binding.isInitialized` guard: "no
     * usable layout yet" is already a case every caller handles.
     */
    private var complicationPlaceholders: Map<Int, FrameLayout> = emptyMap()

    private fun complicationPlaceholder(slotId: Int): FrameLayout? = complicationPlaceholders[slotId]

    /**
     * Where [slotId] is actually being drawn this frame, as fractional unit-square (canvas-relative)
     * bounds, or null when it is hidden. Handed to [WatchFaceComplications.syncGeometry] so the slot's
     * *declared* bounds - which is what taps are tested against - follow what the CWF draws.
     *
     * Read from the laid-out placeholder rather than recomputed from json, so any `DynProvider`
     * top/left offset is already included. Same fractional conversion as [slotBounds], just starting
     * from pixels instead of from the CWF's 400x400 space.
     */
    private fun visibleSlotBounds(slotId: Int): RectF? {
        val width = getWidth().toFloat()
        val height = getHeight().toFloat()
        if (width <= 0f || height <= 0f) return null
        val view = complicationPlaceholder(slotId)?.takeIf { it.isVisible && it.width > 0 && it.height > 0 } ?: return null
        return RectF(view.left / width, view.top / height, view.right / width, view.bottom / height)
    }

    /**
     * The one and only way a slot's **declared** bounds are produced from CWF json. Takes the slot's
     * json section and nothing else - there is deliberately no second, separately-named "default
     * bounds" for a caller to choose between; the fallback lives inside.
     *
     * `ComplicationSlotBounds` are fractional unit-square (canvas-relative), while the CWF json's
     * WIDTH/HEIGHT/TOPMARGIN/LEFTMARGIN are expressed in the fixed 400x400 [TEMPLE_RESOLUTION] space
     * used by every other view. Dividing by it converts directly to the fraction - the `zoomFactor`
     * used elsewhere to reach real display pixels cancels out, a fraction being
     * resolution-independent - so no screen-size lookup is needed here.
     *
     * **The fallback is an empty rect, deliberately.** With no json (or nothing usable in it) the slot
     * has never been positioned by any CWF, and an arbitrary non-empty default would draw a visible box
     * at a location no watch face asked for. Empty means nothing is drawn and nothing is hit-tested
     * until a CWF defines the slot - `ComplicationSlotBounds` accepts an empty rect (its `init`
     * validates only map completeness) and `ComplicationSlot.computeBounds` maps it to `Rect(0,0,0,0)`,
     * since `RectF.intersect` leaves a non-overlapping rect untouched.
     *
     * A fresh `RectF` per call, never a stored one: the `ComplicationSlotBounds(bounds)` convenience
     * constructor does `ComplicationType.values().associateWith { bounds }` and **never copies**, so a
     * shared instance would be reachable from every slot of every engine instance at once.
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
     * A *transversal* section - like `metadata` or `dynPref`, not a per-view block - so it is decoded
     * here, outside [ViewMap], at the same level the json itself places it. Per-slot keys stay in
     * [ViewMap.customizeComplicationView]; these are only the values a slot falls back to when it
     * declares nothing of its own.
     *
     * Style is CWF-wide rather than per-slot because which complication ends up in which slot is the
     * user's choice and can change at any time, so a style authored for one particular data source
     * does not hold up - only one consistent with the CWF's own design language does.
     */
    private class ComplicationGlobalStyle(private val cwf: CustomWatchface) {

        private var json: JSONObject? = null

        /**
         * `DYNPREF`/`DYNDATA` declared on the section itself, so one CWF-wide value (BG level, say)
         * can drive complication style across every slot at once.
         *
         * Only the three values `DynProvider` exposes a per-step getter for can be driven: font
         * colour, background colour and text size. There is no `getTitleColorStep`,
         * `getIconColorStep`, `getRingWidthStep` or `getBorderRadiusStep`, so the rest stay static
         * until `DynProvider` itself gains them.
         */
        private var dynData: DynProvider? = null

        /**
         * Adopts the `complicationStyle` section of a newly loaded CWF, or null when it declares none
         * - in which case every value below resolves to its own default, which is what makes a CWF
         * without the section reset cleanly instead of inheriting the previous CWF's style.
         *
         * Named to match `ViewMap.init` and `DynProvider.init`, the other two places a CWF-wide json
         * block is adopted.
         */
        fun init(json: JSONObject?) {
            this.json = json
            // Built here rather than by customizeViewCommon, which only serves entries that have a
            // View. 0,0 for width/height is safe because nothing in this section is drawable-backed -
            // those arguments only scale drawable steps. If BACKGROUND is ever wired in here, they
            // must become a real size first.
            //
            // The cache key must not collide with any ViewMap key; COMPLICATIONSTYLE's own key is
            // unique by construction. It doubles as getDyn's `defaultViewKey`, which is only a
            // fallback ValueMap name - and no ValueMap is called "complicationStyle", so
            // ValueMap.fromKey lands on NONE. A global dyn block therefore *must* declare VALUEKEY
            // explicitly; unlike a per-view block it has no view name to fall back on.
            dynData = DynProvider.getDyn(
                cwf, json?.optString(JsonKeys.DYNPREF.key) ?: "", json?.optString(JsonKeys.DYNDATA.key) ?: "",
                0, 0, JsonKeys.COMPLICATIONSTYLE.key
            )
        }

        // Computed on every read, like ViewMap's own visibility()/drawable(), so a value always
        // reflects the CWF currently loaded and the live DynProvider step. Each chain ends in a
        // concrete default and never in null: an undeclared key must still produce something to
        // write, or the ComplicationDrawable - which outlives the CWF - keeps the previous one's.
        val fontColor: Int
            get() = dynData?.getFontColorStep(cwf) ?: cwf.getColor(json?.optString(JsonKeys.FONTCOLOR.key) ?: "", Color.WHITE)

        val titleColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.FONTTITLECOLOR.key) ?: "", Color.LTGRAY)

        /** Falls back to [fontColor], which is what the icon followed before ICONCOLOR existed. */
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

        val textSize: Int
            get() = (dynData?.getTextSizeStep(cwf) ?: json?.optInt(JsonKeys.TEXTSIZE.key)?.takeIf { it > 0 })
                ?.let { (it * cwf.zoomFactor).toInt() } ?: ComplicationStyleValues.TEXT_SIZE_FIT_BOX

        val titleSize: Int
            get() = json?.optInt(JsonKeys.TITLESIZE.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: ComplicationStyleValues.TEXT_SIZE_FIT_BOX

        /**
         * Nullable, unlike every other value here, and legitimately so: absent means "no percentage
         * was given", which [ViewMap.ringWidthPx] answers with its own proportional fallback rather
         * than with a percentage. The pixel value that produces is always written, so nothing
         * persists across CWF loads.
         */
        val ringWidthPercent: Int?
            get() = json?.optInt(JsonKeys.RINGWIDTH.key)?.takeIf { it > 0 }

        val ringPrimaryColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.RINGPRIMARYCOLOR.key) ?: "", Color.WHITE)

        val ringSecondaryColor: Int
            get() = cwf.getColor(json?.optString(JsonKeys.RINGSECONDARYCOLOR.key) ?: "", ComplicationStyleValues.RING_SECONDARY_COLOR_DEFAULT)

        /** Ends at the library's own behaviour, so a CWF that says nothing looks exactly as before. */
        val imageFit: ComplicationImageFit
            get() = ImageFitMap.fit(json?.optString(JsonKeys.IMAGEFIT.key), ComplicationImageFit.FIT_CENTER)
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
        // Resolved from the ViewMap entries that declare a slot id, so a new complication slot needs
        // no change here - only its entry, and the matching FrameLayout in activity_custom.xml.
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

    // Defers mainLayout's own draw (chart, cover_chart, ..., cover_plate, hands - unchanged
    // internal order) until after complications have rendered, so complications land behind
    // cover_chart and the analog hands instead of on top of them. background is intentionally not
    // part of mainLayout at all (see resolveBackgroundDrawable()/onDraw()), so it paints first.
    override fun deferMainLayoutDraw(): Boolean = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Nothing of the CWF is drawn in simpleUi mode - mainLayout is skipped by drawMainLayout()
        // and complications by complicationRender(). background used to be inside mainLayout and so
        // was covered by that too; since it was pulled out to be drawn manually here (for z-order,
        // so it paints before complications) it needs the same check, or the CWF background image
        // paints over the simple UI.
        if (simpleUi.isEnabled(currentWatchMode)) return
        // Draws the view's background colour and its image, whichever of the two the CWF ended up
        // with - View.draw() does not care that this view has no parent, only that
        // resolveBackgroundDrawable() measured and laid it out.
        backgroundView.draw(canvas)
        // super.onDraw() has just measured and laid out mainLayout, so the placeholders hold this
        // frame's geometry - the point at which the slots' own bounds can be brought into line with
        // it. Not in complicationRender(): that runs per slot, while one pushed option covers every
        // slot at once.
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
                    // A different zip means the dial now looks like something else, and the system
                    // has no way to know: what it caches its preview from is the style schema, which
                    // says nothing about which CWF is loaded. Asked for here, inside the block that
                    // only runs when the json or the resources actually changed, rather than on
                    // every refresh tick - the system is documented to rate limit these requests.
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
                // Decoded here, with the other CWF-wide values, because `complicationStyle` is a
                // transversal json section like `metadata` or `dynPref` - not a per-view block. The
                // per-slot complication keys are read in ViewMap.customizeComplicationView instead,
                // mirroring where each lives in the json.
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

                        // Complication slots (FrameLayout) get position/size/visibility here like any other
                        // view. The chosen provider is a per-user preference, not stored in the CWF zip.
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
        resolveBackgroundDrawable()
        updateSecondVisibility()
        setSecond() // Update second visibility for time view
        binding.timePeriod.visibility = (binding.timePeriod.isVisible && DateFormat.is24HourFormat(this).not()).toVisibility()
    }

    // background is drawn manually on the Canvas (see onDraw()) from a View kept outside
    // mainLayout, so it can be painted before complications without disturbing mainLayout's own
    // internal draw order (which must stay unchanged: chart, cover_chart, ..., cover_plate, hands,
    // in their existing z-order). Its own styling is not manual - see [backgroundView].
    // Resolved here - same setWatchfaceStyle() pass as everything else - so its BG-level-dependent
    // image (customHigh/customLow, and any DYNDATA step image) never lags a tick behind the rest
    // of the dial; onDraw() just paints whatever this last resolved to, every frame.
    private fun resolveBackgroundDrawable() {
        // Styled exactly like every other image view - drawable, colour steps, built-in fallback,
        // flat colour - because it is the same call, not a second implementation of it.
        ViewMap.BACKGROUND.customizeImageView(backgroundView, this)
        // Then forced to fill the dial and laid out by hand, since nothing else will: this view has
        // no parent to measure it. Same override the View-based version applied (full size, no
        // margins, always visible), so a CWF cannot shrink or hide the one layer that has to cover
        // the screen - it is what stops the previous frame ghosting through.
        val size = (TEMPLE_RESOLUTION * zoomFactor).toInt()
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        backgroundView.measure(spec, spec)
        backgroundView.layout(0, 0, size, size)
    }

    /**
     * Identity of each complication slot this watch face hosts: the [id] the framework knows it by,
     * and the [preferenceKey] of the settings row that opens its data source picker.
     *
     * Both belong together and both are pure constants, so they live here rather than as loose
     * constants: [ViewMap] points at an entry instead of repeating an id, and the settings screens
     * get the whole list through [complicationSlots] instead of hardcoding rows of their own.
     *
     * The ids are a stable public contract - the system persists the chosen data source per (watch
     * face component, slot id) - so they must never change once shipped, and must never be derived
     * from something as movable as an enum ordinal.
     *
     * Note this holds identity only. Live per-slot state (a `ComplicationDrawable` and its
     * invalidate callback) still must not live on an enum: entries are process-wide singletons,
     * while the watch face editor runs a second, headless CustomWatchface concurrently, and the two
     * would share one drawable. That state stays in `ComplicationSlotState`, one per engine.
     */
    private enum class ComplicationMap(
        override val id: Int,
        @StringRes override val preferenceKey: Int,
        @StringRes override val preferenceTitle: Int,
        /** The "Show Complication N" toggle that decides whether this slot is drawn at all. */
        val showPref: PrefMap
    ) : ComplicationSlotInfo {

        COMPLICATION1(101, R.string.key_complication_1, R.string.pref_complication_1, PrefMap.SHOW_COMPLICATION_1),
        COMPLICATION2(102, R.string.key_complication_2, R.string.pref_complication_2, PrefMap.SHOW_COMPLICATION_2),
        COMPLICATION3(103, R.string.key_complication_3, R.string.pref_complication_3, PrefMap.SHOW_COMPLICATION_3),
        COMPLICATION4(104, R.string.key_complication_4, R.string.pref_complication_4, PrefMap.SHOW_COMPLICATION_4),
        COMPLICATION5(105, R.string.key_complication_5, R.string.pref_complication_5, PrefMap.SHOW_COMPLICATION_5)
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
         * The complication slot this entry carries the geometry for, or null for every ordinary
         * view. Non-null is what marks an entry as a complication placeholder, and
         * [WatchFaceComplications] gets its whole slot list from [ComplicationMap] - so a new slot is
         * one new entry there plus one here, not a third parallel list to keep in sync.
         *
         * Safe as enum state precisely because it is an immutable constructor constant, exactly like
         * [pref] or [key]. The state that must **not** live on this enum is live per-engine state -
         * a `ComplicationDrawable`, [viewJson], [width] - because entries are process-wide
         * singletons and the editor runs a second headless CustomWatchface concurrently.
         */
        val complication: ComplicationMap? = null
    ) {

        BACKGROUND(
            key = ViewKeys.BACKGROUND.key,
            // NO_ID because its View is not in mainLayout, so the loop that customizes views by id
            // must not find it - CustomWatchface.resolveBackgroundDrawable() styles it instead.
            id = View.NO_ID,
            defaultDrawable = R.drawable.background,
            customDrawable = ResFileMap.BACKGROUND,
            customHigh = ResFileMap.BACKGROUND_HIGH,
            customLow = ResFileMap.BACKGROUND_LOW
        ),
        // Plain FrameLayout placeholders that carry position/size/visibility/rotation for the real
        // ComplicationSlots (see ComplicationHost / customizeComplicationView). They draw nothing
        // themselves - the framework renders the actual complication onto the Canvas.
        //
        // The json key, the placeholder view id, the visibility preference and the slot this entry
        // draws for all meet here; the slot's own identity (id + settings row) lives in
        // [ComplicationMap].
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
             * Ring thickness in physical pixels for `RANGED_VALUE`/`GOAL_PROGRESS`, from the slot's
             * declared size so it stays proportional to the complication rather than being the
             * library's fixed hairline. Pure arithmetic - the json reads that feed it happen in
             * [customizeComplicationView], like every other key in that block.
             *
             * **Units.** [declaredWidth] is in the CWF's 400x400 space, so it is multiplied by
             * [zoomFactor] to reach physical pixels - the same conversion [customizeViewCommon]
             * applies to every other view. `ComplicationStyle.rangedValueRingWidth` is `@Px`, i.e.
             * physical pixels, so the result is already in the right unit. [ComplicationStyleValues.MIN_RING_WIDTH_PX] is in
             * that same unit and is deliberately **not** scaled: an absolute floor, not a design
             * value.
             *
             * **Why not the library's own default?** Its effective default is `2dp`
             * (`R.dimen.complicationDrawable_rangedValueRingWidth`, applied by
             * `ComplicationDrawable.setContext`), and it must never be multiplied by [zoomFactor]:
             * `dp` is already a complete device-specific conversion belonging to Android's
             * physical-size resolution-independence system, while [zoomFactor] belongs to this watch
             * face's own - proportion of the declared 400x400 space. Combining them double-applies
             * two unrelated scaling schemes. Using `2dp` *unscaled* was also rejected: a fixed
             * physical thickness regardless of how large the CWF declared the slot would make ring
             * width the one CWF dimension that does not scale with its slot, breaking the
             * reproducible-across-devices design the format is built on. The library offers no
             * size-relative default at all - `ComplicationRenderer` uses the value directly as a
             * stroke width - so a proportional value computed here is the only option consistent with
             * every other CWF-declared dimension. Do not reintroduce `2dp` as a "fix".
             *
             * **Where the ratio comes from.** A visual comparison, during this feature's development,
             * against a Samsung Watch Face Format watch face whose ring measured roughly 1/6 of the
             * complication radius; radius is width/2, hence width/12. That face does not use this
             * renderer (proven - see `_docs/Complication_Libraries.md`), so it is a design target
             * rather than a like-for-like measurement, and no upstream guideline exists.
             *
             * Rounds rather than truncates, matching `ComplicationSlot.computeBounds`, which adds 0.5
             * before `toInt()` for exactly this reason.
             */
            fun ringWidthPx(declaredWidth: Int, percent: Int?, zoomFactor: Double): Int {
                if (declaredWidth <= 0) return ComplicationStyleValues.MIN_RING_WIDTH_PX
                val slotWidthPx = declaredWidth * zoomFactor
                // Clamped rather than trusted: the ring is stroked centred on the arc, so a value
                // beyond 100 would paint outside the slot, and a negative one is meaningless. 100 is
                // already absurd in practice (the ring would swallow the whole complication) but it
                // is the honest upper bound of "percentage of the declared width".
                val raw = percent?.let { slotWidthPx * it.coerceIn(1, 100) / 100.0 }
                    ?: (slotWidthPx / PROVISIONAL_RING_WIDTH_RATIO)
                return (0.5 + raw).toInt().coerceAtLeast(ComplicationStyleValues.MIN_RING_WIDTH_PX)
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
         * The preference that gates this view: its own [pref], or - for a complication placeholder -
         * the one its slot carries, so the slot's id and its "Show Complication N" toggle stay
         * declared together in [ComplicationMap] instead of half here and half there.
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
         * Every key of the `complication1/2/3` json block is read here, directly off [viewJson], the
         * same way [customizeTextView] reads its own - the decode site follows the json's structure,
         * not whether another view type happens to have an equivalent concept. `dynData` is wired
         * identically too: [customizeViewCommon] has already built it from this block's `DYNPREF` /
         * `DYNDATA`, so the per-step getters below are the same ones every other view uses, with no
         * complication-specific DynProvider logic anywhere. The only difference from
         * [customizeTextView] is where each chain *ends*: at the CWF-wide `complicationStyle` section
         * rather than at a literal.
         */
        fun customizeComplicationView(view: FrameLayout, cwf: CustomWatchface) {
            // Called first and unconditionally, and nothing below may short-circuit it: this is what
            // gives a complication every geometry behaviour an ordinary view has - DynProvider
            // top/left/rotation offsets, and the twinView + TOPOFFSETTWINHIDDEN/LEFTOFFSETTWINHIDDEN
            // space-ceding mechanism - rather than a reimplemented subset of them. It also builds
            // dynData, which the reads below depend on.
            customizeViewCommon(view, cwf)
            val state = complication?.let { cwf.complications.stateFor(it.id) } ?: return
            val global = cwf.complicationStyle
            // Each chain: this slot's dynData step (only where DynProvider actually exposes one),
            // then this slot's own json key, then the CWF-wide complicationStyle value. dynData is
            // the very same instance customizeViewCommon just built from this block's DYNPREF/DYNDATA
            // to drive the placeholder's offsets - no complication-specific DynProvider logic exists.
            val textColor = dynData?.getFontColorStep(cwf)
                ?: viewJson?.optString(JsonKeys.FONTCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.fontColor) }
                ?: global.fontColor
            // No getTitleColorStep/getIconColorStep on DynProvider, so these two start at the json.
            val titleColor = viewJson?.optString(JsonKeys.FONTTITLECOLOR.key)?.takeIf { it.isNotEmpty() }
                ?.let { cwf.getColor(it, global.titleColor) } ?: global.titleColor
            // ICONCOLOR wins; without it the icon follows this slot's own text colour, which is what
            // it did before the key existed; only if neither is declared does the global value apply.
            val iconColor = viewJson?.optString(JsonKeys.ICONCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.iconColor) }
                ?: viewJson?.optString(JsonKeys.FONTCOLOR.key)?.takeIf { it.isNotEmpty() }?.let { cwf.getColor(it, global.iconColor) }
                ?: global.iconColor
            // `has` rather than the emptiness test used above: a COLOR key explicitly present but
            // empty resolves through getColor's own default instead of falling back. Kept from before
            // the refactor - the keys disagree on this and unifying them is a deliberate future
            // decision, not something to change silently here.
            val backgroundColor = dynData?.getColorStep(cwf)
                ?: viewJson?.let { json -> if (json.has(JsonKeys.COLOR.key)) cwf.getColor(json.optString(JsonKeys.COLOR.key)) else null }
                ?: global.backgroundColor
            val textTypeface = typefaceOf(viewJson, JsonKeys.FONT.key, JsonKeys.FONTSTYLE.key, global.font)
            val titleTypeface = typefaceOf(viewJson, JsonKeys.FONTTITLE.key, JsonKeys.TITLESTYLE.key, global.titleFont)
            // Sizes are @Px upper bounds - ComplicationRenderer hands them to Paint.setTextSize and
            // TextRenderer then shrinks to fit - so they take the same zoomFactor scaling as every
            // other CWF dimension. The global values arrive already scaled.
            val textSize = (dynData?.getTextSizeStep(cwf) ?: viewJson?.optInt(JsonKeys.TEXTSIZE.key)?.takeIf { it > 0 })
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.textSize
            val titleSize = viewJson?.optInt(JsonKeys.TITLESIZE.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.titleSize
            val borderRadius = viewJson?.optInt(JsonKeys.BORDERRADIUS.key)?.takeIf { it > 0 }
                ?.let { (it * cwf.zoomFactor).toInt() } ?: global.borderRadius
            val ringWidth = ringWidthPx(
                viewJson?.optInt(JsonKeys.WIDTH.key) ?: 0,
                viewJson?.optInt(JsonKeys.RINGWIDTH.key)?.takeIf { it > 0 } ?: global.ringWidthPercent,
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
                ringSecondaryColor = ringSecondaryColor
            )
            state.applyStyle()
            // Not part of the style values: nothing in ComplicationDrawable holds it, the renderer
            // reads it per frame instead. Same cascade as everything above - this slot, then the
            // CWF-wide block, then the library's own behaviour.
            state.imageFit = ImageFitMap.fit(viewJson?.optString(JsonKeys.IMAGEFIT.key), global.imageFit)
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
         * The label of this preference's row on the watch face's settings screen, or null when it has
         * no row.
         *
         * Taken from [metadataKey] wherever there is one, so a preference is labelled by the very
         * string the phone shows for it, rather than by a second copy of that text in this module.
         * Declared here rather than in a settings xml so the row and the preference cannot drift
         * apart - the screen is built from these entries, see `CustomWatchfaceConfigurationFragment`.
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