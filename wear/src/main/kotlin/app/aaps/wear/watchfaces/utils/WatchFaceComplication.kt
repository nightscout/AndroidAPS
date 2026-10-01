package app.aaps.wear.watchfaces.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.support.wearable.complications.ComplicationData as WireComplicationData
import androidx.annotation.StringRes
import androidx.core.graphics.withClip
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.toApiComplicationData
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationStyle
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption
import androidx.wear.watchface.style.WatchFaceLayer
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

/*
 * Everything a watch face needs in order to host androidx complication slots, with no knowledge of
 * any particular watch face: geometry and style arrive as plain values and lambdas, never as a
 * watch-face format, a json key or a layout.
 *
 * Library behaviour referenced by the comments below is documented, with versions and line numbers,
 * in `_docs/Complication_Libraries.md`.
 */

/**
 * What kind of content a slot should be offered first when the user picks a data source.
 *
 * The system walks a slot's `supportedTypes` in order and takes the first type the chosen provider
 * also offers. Every value below lists *every* type, only the order differs, so a choice a provider
 * cannot satisfy degrades to the next best type instead of breaking the slot.
 */
internal enum class ComplicationTypePriority {

    /** Data first: a number or a gauge. */
    VALUE,

    /** Text first. */
    TEXT,

    /** Image first - the only order that reaches an icon-only provider's icon. */
    ICON;

    /** The full type list in this priority's order. Never a subset. */
    fun supportedTypes(): List<ComplicationType> {
        val dataBearing = buildList {
            add(ComplicationType.RANGED_VALUE)
            // GOAL_PROGRESS/WEIGHTED_ELEMENTS are @RequiresApi(TIRAMISU).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(ComplicationType.GOAL_PROGRESS)
                add(ComplicationType.WEIGHTED_ELEMENTS)
            }
        }
        val text = listOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT)
        val images = listOf(ComplicationType.MONOCHROMATIC_IMAGE, ComplicationType.SMALL_IMAGE, ComplicationType.PHOTO_IMAGE)
        return when (this) {
            VALUE -> dataBearing + text + images
            TEXT  -> text + dataBearing + images
            ICON  -> images + text + dataBearing
        }
    }
}

/** One complication slot of a watch face, as seen by code that must not name a watch face. */
internal interface ComplicationSlotInfo {

    /**
     * The slot id the framework knows this slot by. Stable forever once shipped: the system persists
     * the chosen data source per (watch face component, slot id).
     */
    val id: Int

    /** Key of the settings row that opens this slot's data source picker. */
    @get:StringRes val preferenceKey: Int

    /** Label of that row. */
    @get:StringRes val preferenceTitle: Int
}

/**
 * The complication slots a watch face hosts, in the order its settings screen presents them.
 *
 * Implemented by the watch face itself, since only it knows whether its slots are a fixed list or one
 * derived from a loaded template. Nothing about visibility or user settings belongs here.
 */
internal interface WatchFaceComplicationSlots {

    val complicationSlots: List<ComplicationSlotInfo>
}

/** How a watch face wants one [ComplicationSlot] painted for the frame being rendered. */
internal sealed interface ComplicationRender {

    /** Paint at the slot's own declared bounds - what the framework does on its own. */
    data object Declared : ComplicationRender

    /** Don't paint this slot at all this frame. */
    data object Skip : ComplicationRender

    /** Paint into [bounds], rotated [rotation] degrees about its centre. */
    data class At(val bounds: Rect, val rotation: Float = 0f) : ComplicationRender
}

/**
 * How an image-only complication (`SMALL_IMAGE`, `PHOTO_IMAGE`) fills its slot.
 *
 * Values mean what `android.widget.ImageView.ScaleType` means. None of them is expressible through
 * `ComplicationDrawable`, which always crops the image to the slot's central square - which is why
 * `SyncLoadingCanvasComplication` draws these two types itself.
 */
internal enum class ComplicationImageFit {

    /** Whole image, aspect kept, centred - letterboxed if the slot is a different shape. */
    FIT_CENTER,

    /** Aspect kept, scaled until the slot is covered, overflow cropped. */
    CENTER_CROP,

    /** Stretched to the slot's width and height. The only value that does not keep aspect ratio. */
    FIT_XY;

    /** Where an [imageWidth] x [imageHeight] image goes inside [bounds] under this fit. */
    fun destination(imageWidth: Int, imageHeight: Int, bounds: Rect): Rect {
        if (imageWidth <= 0 || imageHeight <= 0) return bounds
        if (this == FIT_XY) return bounds
        val scaleX = bounds.width().toFloat() / imageWidth
        val scaleY = bounds.height().toFloat() / imageHeight
        val scale = if (this == CENTER_CROP) max(scaleX, scaleY) else min(scaleX, scaleY)
        val width = (imageWidth * scale).toInt()
        val height = (imageHeight * scale).toInt()
        val left = bounds.centerX() - width / 2
        val top = bounds.centerY() - height / 2
        return Rect(left, top, left + width, top + height)
    }
}

/**
 * Whether a complication's small image should be dropped so the monochromatic icon underneath it
 * renders instead.
 *
 * Pure and Android-free so the rules are stated once and covered by `ComplicationPayloadPolicyTest`.
 *
 * @param wireType `android.support.wearable.complications.ComplicationData` type constant - an Int
 *   rather than the `ComplicationType` enum, whose GOAL_PROGRESS/WEIGHTED_ELEMENTS are
 *   `@RequiresApi(TIRAMISU)`.
 * @param iconIsLoadable whether the monochromatic icon resolves to a drawable. `hasIcon` only says
 *   the field is set, which is not the same thing.
 * @param iconColorRequested whether the watch face asked for a specific icon colour, which only a
 *   monochromatic image can honour.
 */
// The wire ComplicationData class is @RestrictTo(LIBRARY_GROUP) - lint only, nothing enforces it at
// runtime, and a future rename breaks the build rather than failing silently.
@Suppress("RestrictedApi")
internal fun shouldDropSmallImage(
    wireType: Int,
    hasSmallImage: Boolean,
    hasIcon: Boolean,
    iconIsLoadable: Boolean,
    iconColorRequested: Boolean
): Boolean {
    // Nothing to drop, or nothing to fall back on.
    if (!hasSmallImage || !hasIcon) return false
    // Never trade a working image for one that cannot draw - that leaves no image at all.
    if (!iconIsLoadable) return false
    // The ranged-value family hits a layout bug that renders neither image, so the drop is the only
    // way to see anything there, colour requested or not.
    val routedThroughRangedValueLayout =
        wireType == WireComplicationData.TYPE_RANGED_VALUE ||
            wireType == WireComplicationData.TYPE_GOAL_PROGRESS ||
            wireType == WireComplicationData.TYPE_WEIGHTED_ELEMENTS
    if (routedThroughRangedValueLayout) return true
    // Other types render the small image correctly in the provider's own colours; only an explicit
    // colour request justifies giving that up.
    return iconColorRequested
}

/**
 * Renderer for a complication slot. Every watch face hosting slots should use this rather than
 * [CanvasComplicationDrawable] directly, for three reasons:
 *
 * 1. It forces the synchronous branch of `ComplicationDrawable.setComplicationData`. The async branch
 *    installs a second `ComplicationRenderer` only once an image load completes, and on the
 *    engine-start path that never happens - leaving a complication that reports correct data and
 *    accepts taps but paints nothing until its provider is re-picked.
 * 2. It drops a redundant small image - see `withoutRedundantSmallImage`.
 * 3. It applies [ComplicationImageFit] - see [render].
 *
 * @param imageFit read per frame, so a watch face can change it without rebuilding the slot.
 */
internal class SyncLoadingCanvasComplication(
    private val context: Context,
    drawable: ComplicationDrawable,
    watchState: WatchState,
    invalidateCallback: CanvasComplication.InvalidateCallback,
    private val imageFit: () -> ComplicationImageFit = { ComplicationImageFit.FIT_CENTER },
    private val iconColorRequested: () -> Boolean = { false }
) : CanvasComplicationDrawable(drawable, watchState, invalidateCallback) {

    /** The data as the system sent it, so [reapplyIconColorRequest] can start over from it. */
    private var receivedData: ComplicationData? = null

    /**
     * Always loads synchronously - do not pass [loadDrawablesAsynchronous] through, that reintroduces
     * the blank-until-re-picked bug described on the class.
     */
    override fun loadData(complicationData: ComplicationData, loadDrawablesAsynchronous: Boolean) {
        receivedData = complicationData
        super.loadData(prepared(complicationData), false)
    }

    /**
     * Re-runs [prepared] on the data already received, for when the answer to [iconColorRequested]
     * changes - the system only calls [loadData] when *it* has new data.
     *
     * Starts from [receivedData] so the change is reversible, and pushes only when the payload would
     * actually differ: an unnecessary write into the drawable has been observed to disturb rendering.
     */
    fun reapplyIconColorRequest() {
        val data = receivedData ?: return
        val prepared = prepared(data)
        if (prepared !== data) super.loadData(prepared, false)
    }

    private fun prepared(data: ComplicationData) = withoutRedundantSmallImage(data)

    /**
     * Draws the complication, taking over the image for `SMALL_IMAGE`/`PHOTO_IMAGE` so
     * [ComplicationImageFit] can mean what it says.
     *
     * The library crops the image to the slot's central square before any caller can influence it, so
     * no bounds or canvas trick can recover the lost sides. Drawing here uses the image's intrinsic
     * size instead. Only these two types are taken over - they carry no text fields; every other type
     * keeps the library's layout.
     *
     * Background, corner radius and border are reproduced from the same [ComplicationStyle] the
     * library would have used, so those style values behave as they do for every other type.
     */
    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, renderParameters: RenderParameters, slotId: Int) {
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT
        val image = imageOf(getData(), ambient)
        if (image == null || bounds.isEmpty) {
            super.render(canvas, bounds, zonedDateTime, renderParameters, slotId)
            return
        }
        val style = if (ambient) drawable.ambientStyle else drawable.activeStyle
        // Clamped the way ComplicationRenderer clamps it, so the shape matches a library-drawn slot.
        val radius = min(style.borderRadius.toFloat(), min(bounds.width(), bounds.height()) / 2f)
        canvas.withClip(Path().apply { addRoundRect(RectF(bounds), radius, radius, Path.Direction.CW) }) {
            if (style.backgroundColor != Color.TRANSPARENT) drawColor(style.backgroundColor)
            image.bounds = imageFit().destination(image.intrinsicWidth, image.intrinsicHeight, bounds)
            image.draw(this)
        }
        // Last and unclipped, same as the library: the stroke is centred on the slot edge, so half of
        // it paints outside the declared bounds.
        if (style.borderWidth > 0 && Color.alpha(style.borderColor) > 0) {
            borderPaint.color = style.borderColor
            borderPaint.strokeWidth = style.borderWidth.toFloat()
            canvas.drawRoundRect(RectF(bounds), radius, radius, borderPaint)
        }
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * The image this complication carries, or null for every type left to the library.
     *
     * Loading an [Icon] decodes, so the result is cached until the icon instance itself changes.
     */
    private fun imageOf(data: ComplicationData, ambient: Boolean): Drawable? {
        val icon = when (data) {
            is SmallImageComplicationData -> data.smallImage.ambientImage.takeIf { ambient } ?: data.smallImage.image
            is PhotoImageComplicationData -> data.photoImage
            else                          -> null
        } ?: return null
        if (icon !== loadedIcon) {
            loadedIcon = icon
            loadedImage = icon.loadDrawable(context)
        }
        return loadedImage
    }

    private var loadedIcon: Icon? = null
    private var loadedImage: Drawable? = null

    /**
     * Drops the small image when [shouldDropSmallImage] says the monochromatic icon should render
     * instead - either to dodge the ranged-value layout bug that renders neither image, or because
     * the watch face asked for an icon colour, which only a monochromatic image can honour.
     *
     * Copies the wire object rather than rebuilding through the API-level builders, which expose no
     * setters for `dataSource`, `persistencePolicy`, `displayPolicy` or `extras` and would silently
     * discard them.
     */
    @Suppress("RestrictedApi")
    private fun withoutRedundantSmallImage(complicationData: ComplicationData): ComplicationData {
        val wire = complicationData.asWireComplicationData()
        val drop = shouldDropSmallImage(
            wireType = wire.type,
            hasSmallImage = wire.hasSmallImage(),
            hasIcon = wire.hasIcon(),
            // Resolved only when the answer could still be yes - loading an icon is not free.
            iconIsLoadable = wire.hasIcon() && wire.hasSmallImage() && wire.icon?.loadDrawable(context) != null,
            iconColorRequested = iconColorRequested()
        )
        // Returning the same instance matters: reapplyIconColorRequest() treats identity as "nothing
        // to do" and skips pushing into the drawable.
        if (!drop) return complicationData
        return WireComplicationData.Builder(wire)
            .setSmallImage(null)
            .setBurnInProtectionSmallImage(null)
            .build()
            .toApiComplicationData()
    }
}

/**
 * The presentation a watch face wants for one complication, and the only thing that writes it into a
 * [ComplicationDrawable].
 *
 * A value object so "nothing declared" is one named instance rather than a default repeated per
 * field, and so equality comes for free.
 */
internal data class ComplicationStyleValues(
    val textColor: Int,
    val titleColor: Int,
    val iconColor: Int,
    val backgroundColor: Int,
    val textTypeface: Typeface,
    val titleTypeface: Typeface,
    val borderRadius: Int,
    val ringWidth: Int,
    val textSize: Int,
    val titleSize: Int,
    val ringPrimaryColor: Int,
    val ringSecondaryColor: Int,
    /**
     * Colour of the border, transparent for "no border". There is deliberately no border *style*
     * alongside it: `borderStyle` is pinned to solid in [applyTo], and the library implements
     * `BORDER_STYLE_NONE` as a zero-alpha border paint, so the two would be redundant knobs.
     */
    val borderColor: Int,
    /**
     * Border thickness in real pixels. Purely cosmetic - unlike [borderRadius] it takes no part in
     * layout, so it never shrinks the space a complication has to draw in. The stroke is centred on
     * the slot edge and is not clipped, so half of it paints outside the declared bounds.
     */
    val borderWidth: Int
) {

    /**
     * Writes every value into both style slots of [drawable].
     *
     * Every property is written on every call, never conditionally: a `ComplicationDrawable` outlives
     * the watch-face configuration that last styled it, so a skipped write would silently keep the
     * previous configuration's value.
     *
     * Note [borderRadius] also insets the *content* area (androidx defaults it to `Int.MAX_VALUE`,
     * which costs ~41% of the box) and shapes the background fill.
     */
    fun applyTo(drawable: ComplicationDrawable) {
        for (style in listOf(drawable.activeStyle, drawable.ambientStyle)) {
            style.borderStyle = ComplicationStyle.BORDER_STYLE_SOLID
            style.borderColor = borderColor
            style.borderWidth = borderWidth
            style.borderRadius = borderRadius
            style.rangedValueRingWidth = ringWidth
            style.backgroundColor = backgroundColor
            style.textColor = textColor
            style.titleColor = titleColor
            style.iconColor = iconColor
            style.setTextTypeface(textTypeface)
            style.setTitleTypeface(titleTypeface)
            style.textSize = textSize
            style.titleSize = titleSize
            style.rangedValuePrimaryColor = ringPrimaryColor
            style.rangedValueSecondaryColor = ringSecondaryColor
        }
    }

    companion object {

        /** Absolute floor in real pixels, so a very small slot still gets a visible ring. */
        const val MIN_RING_WIDTH_PX = 2

        /**
         * "As large as the box allows" for `textSize`/`titleSize`, which are upper bounds that
         * `TextRenderer` shrinks to fit. Must be written explicitly: a context-attached drawable
         * starts from a 14sp resource default instead.
         */
        const val TEXT_SIZE_FIT_BOX = Int.MAX_VALUE

        /** androidx's *resource* default for `rangedValueSecondaryColor` (50% white). */
        const val RING_SECONDARY_COLOR_DEFAULT = 0x80FFFFFF.toInt()

        /** What a slot gets before any watch-face styling has been decoded. */
        val LIBRARY_DEFAULTS = ComplicationStyleValues(
            textColor = Color.WHITE,
            titleColor = Color.LTGRAY,
            iconColor = Color.WHITE,
            backgroundColor = Color.TRANSPARENT,
            textTypeface = Typeface.DEFAULT,
            titleTypeface = Typeface.DEFAULT,
            borderRadius = 0,
            ringWidth = MIN_RING_WIDTH_PX,
            textSize = TEXT_SIZE_FIT_BOX,
            titleSize = TEXT_SIZE_FIT_BOX,
            ringPrimaryColor = Color.WHITE,
            ringSecondaryColor = RING_SECONDARY_COLOR_DEFAULT,
            borderColor = Color.TRANSPARENT,
            borderWidth = 0
        )
    }
}

/**
 * Live state for one complication slot, scoped to one engine instance.
 *
 * [drawable] is why this cannot be static or enum state: it is bound to the engine that created it,
 * and the watch face editor runs a second, headless instance concurrently.
 *
 * [applyStyle] is called from both the drawable-created and style-decoded paths, since renderers are
 * created lazily and either can happen first.
 */
internal class ComplicationSlotState(val slotId: Int) {

    var drawable: ComplicationDrawable? = null

    var style: ComplicationStyleValues = ComplicationStyleValues.LIBRARY_DEFAULTS

    /** Read on the render thread every frame; nothing in `ComplicationDrawable` holds it. */
    @Volatile var imageFit: ComplicationImageFit = ComplicationImageFit.FIT_CENTER

    /** Set by [WatchFaceComplications] when the slot's renderer is created. */
    internal var renderer: SyncLoadingCanvasComplication? = null

    /**
     * Whether the watch face asked for a specific icon colour on this slot. Setting it re-runs the
     * payload decision on data already received, since the system only calls `loadData` when it has
     * something new.
     */
    @Volatile var iconColorRequested: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            renderer?.reapplyIconColorRequest()
        }

    /** Pushes [style] into the drawable, or does nothing until one exists. */
    fun applyStyle() = drawable?.let { style.applyTo(it) }
}

/**
 * Hosts a set of androidx complication slots for one watch face engine.
 *
 * @param slotIds the slots to host, in the watch face's own order
 * @param supportedTypesFor the types a given slot accepts, in preference order - the system takes the
 *   first entry the chosen provider also supports. Per slot because the useful order depends on that
 *   provider; see [ComplicationTypePriority]. Read once at slot creation.
 * @param layoutSettingLabel label for the slot-layout user-style setting, shown by a system editor
 * @param layoutSettingDescription its description
 */
internal class WatchFaceComplications(
    private val context: Context,
    private val slotIds: List<Int>,
    private val supportedTypesFor: (slotId: Int) -> List<ComplicationType>,
    @StringRes private val layoutSettingLabel: Int,
    @StringRes private val layoutSettingDescription: Int
) {

    companion object {

        private const val SLOT_STYLE_SETTING_ID = "wf_slots"
        private const val SLOT_STYLE_OPTION_ID_DEFAULT = "wf_d"
        private const val SLOT_STYLE_OPTION_ID_PREFIX = "wf_"
    }

    private val slotStates: Map<Int, ComplicationSlotState> = slotIds.associateWith { ComplicationSlotState(it) }

    // Created on a background thread, read on the render thread in syncGeometry() - hence @Volatile.
    @Volatile private var slotsSetting: ComplicationSlotsUserStyleSetting? = null
    @Volatile private var userStyleRepository: CurrentUserStyleRepository? = null
    @Volatile private var slotsManager: ComplicationSlotsManager? = null

    // Keeps every pushed option's id distinct - see syncGeometry().
    private var geometryRevision = 0

    /** The live state for [slotId], for a watch face to write its decoded style into. */
    fun stateFor(slotId: Int): ComplicationSlotState? = slotStates[slotId]

    /**
     * Declares the one [ComplicationSlotsUserStyleSetting] that [syncGeometry] needs in order to move
     * slots at runtime. A watch face whose slot geometry never changes need not call this.
     *
     * The single option carries no overlays, so by itself this schema changes nothing; it exists only
     * because `updateUserStyle` validates that the setting belongs to the schema.
     */
    fun createUserStyleSchema(): UserStyleSchema {
        val defaultOption = ComplicationSlotsOption(
            UserStyleSetting.Option.Id(SLOT_STYLE_OPTION_ID_DEFAULT),
            context.resources,
            layoutSettingLabel,
            layoutSettingLabel,
            null,
            emptyList()
        )
        val setting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id(SLOT_STYLE_SETTING_ID),
            context.resources,
            layoutSettingLabel,
            layoutSettingDescription,
            null,
            listOf(defaultOption),
            // The library requires this setting to affect the complications layer.
            listOf(WatchFaceLayer.COMPLICATIONS)
        )
        slotsSetting = setting
        return UserStyleSchema(listOf(setting))
    }

    /**
     * Builds the slots, asking [initialBoundsFor] where each one starts.
     *
     * Everything decided here is creation-time only: `ComplicationSlot.Builder` arguments are fixed
     * once built, and bounds and enabled state can afterwards be changed solely through [syncGeometry].
     */
    fun createSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository,
        initialBoundsFor: (slotId: Int) -> ComplicationSlotBounds
    ): ComplicationSlotsManager {
        userStyleRepository = currentUserStyleRepository
        val slots = slotStates.values.map { state -> buildSlot(state, initialBoundsFor(state.slotId)) }
        return ComplicationSlotsManager(slots, currentUserStyleRepository).also { slotsManager = it }
    }

    private fun buildSlot(state: ComplicationSlotState, bounds: ComplicationSlotBounds): ComplicationSlot {
        val canvasComplicationFactory = CanvasComplicationFactory { watchState, invalidateCallback ->
            // Renderers are created lazily, so this may run before or after the watch face decodes its
            // style; applying here covers "drawable created last", the decode pass covers the reverse.
            val drawable = ComplicationDrawable(context)
            state.drawable = drawable
            state.applyStyle()
            SyncLoadingCanvasComplication(
                context = context,
                drawable = drawable,
                watchState = watchState,
                invalidateCallback = invalidateCallback,
                imageFit = { state.imageFit },
                iconColorRequested = { state.iconColorRequested }
            ).also { state.renderer = it }
        }
        return ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = state.slotId,
            canvasComplicationFactory = canvasComplicationFactory,
            supportedTypes = supportedTypesFor(state.slotId),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = bounds
        ).build()
    }

    /**
     * Brings each slot's *declared* bounds and enabled state into line with what is actually drawn,
     * which is what makes taps land correctly after a watch face moves a slot.
     *
     * [visibleBoundsFor] returns fractional, unit-square bounds, or null when the slot is hidden - in
     * which case it is pushed as disabled with empty bounds, which also stops it receiving data.
     *
     * Painting elsewhere (see [ComplicationRender]) only fixes what the user sees: tap hit-testing
     * reads the slot's own cached bounds. Those setters are `internal`, so the only supported way to
     * write them is a user-style change, which also keeps accessibility bounds and editor previews
     * right.
     *
     * Three constraints, none of them obvious:
     * - **Every push needs a fresh option id.** `UserStyleSetting.Option.equals` compares the id only,
     *   and an option considered equal to the previous one is ignored - new bounds would be accepted
     *   and silently never applied.
     * - **Compare against the slots, not against what was last pushed.** An applied option that
     *   carries no overlay for a slot resets that slot to its *initial* bounds, and the schema's own
     *   default option carries none - so a style change from anywhere else can silently undo this.
     *   Comparing intent to intent would never notice, leaving taps landing nowhere.
     * - `updateUserStyle` is `@RestrictTo(LIBRARY_GROUP)`, hence the suppression; `validateUserStyle`
     *   deliberately does not require the option to be one the schema enumerated, which is what makes
     *   runtime-computed bounds legal.
     *
     * Safe to call once per frame: it pushes only when a slot is not already where it should be.
     */
    @Suppress("RestrictedApi")
    fun syncGeometry(visibleBoundsFor: (slotId: Int) -> RectF?) {
        val setting = slotsSetting ?: return
        val repository = userStyleRepository ?: return
        val manager = slotsManager ?: return

        val overlays = mutableListOf<ComplicationSlotOverlay>()
        var slotOutOfDate = false
        for (state in slotStates.values) {
            val bounds = visibleBoundsFor(state.slotId)
            val enabled = bounds != null
            val wanted = ComplicationSlotBounds(bounds ?: RectF())
            // Builder rather than the constructor: the 4-argument secondary constructor is deprecated
            // and named arguments resolve to it.
            overlays += ComplicationSlotOverlay.Builder(state.slotId)
                .setEnabled(enabled)
                .setComplicationSlotBounds(wanted)
                .build()
            val slot = manager[state.slotId]
            if (slot == null || slot.enabled != enabled || slot.complicationSlotBounds.perComplicationTypeBounds != wanted.perComplicationTypeBounds)
                slotOutOfDate = true
        }

        if (!slotOutOfDate) return

        val option = ComplicationSlotsOption(
            UserStyleSetting.Option.Id("$SLOT_STYLE_OPTION_ID_PREFIX${++geometryRevision}"),
            context.resources,
            layoutSettingLabel,
            layoutSettingLabel,
            null,
            overlays
        )
        repository.updateUserStyle(
            repository.userStyle.value.toMutableUserStyle().apply { set(setting, option) }.toUserStyle()
        )
    }
}
