package app.aaps.wear.watchfaces.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData as WireComplicationData
import androidx.annotation.StringRes
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
 * any particular watch face.
 *
 * The split this file draws: a watch face decides **what** a complication should look like and
 * **where** it sits; this file knows **how** to make androidx do it. So nothing here reads a
 * watch-face format, a json key or a layout - geometry and style arrive as plain values and lambdas.
 *
 * It also carries the workarounds for androidx bugs that any watch face would hit identically -
 * see [SyncLoadingCanvasComplication].
 */

/**
 * One complication slot of a watch face, as seen by code that must work for any watch face without
 * naming one - the data source picker above all.
 */
internal interface ComplicationSlotInfo {

    /**
     * The slot id the framework knows this slot by. Stable forever once shipped: the system persists
     * the chosen data source per (watch face component, slot id).
     */
    val id: Int

    /** The key of the settings row that opens this slot's data source picker. */
    @get:StringRes val preferenceKey: Int

    /** The label of that row. */
    @get:StringRes val preferenceTitle: Int
}

/**
 * The complication slots a watch face hosts.
 *
 * Implemented by the watch face itself (typically its companion object), because only it knows what
 * its slots are: a fixed, constant list for a watch face with a hardcoded layout, or a list derived
 * from a loaded template for one like the custom watch face. Both look the same from here.
 *
 * Nothing about visibility or user settings belongs in this contract. Which slots a watch face hosts
 * is a property of the watch face; whether the user currently wants one shown is not, and is decided
 * by the watch face or by its settings screen, never by the code reading this.
 */
internal interface WatchFaceComplicationSlots {

    /**
     * The slots this watch face hosts, in the order its settings screen presents them. Empty for a
     * watch face with no complications.
     */
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
 * Values mean what `android.widget.ImageView.ScaleType` means, and are computed from the image's own
 * intrinsic size - which is only possible because this file draws those images itself. The library
 * cannot express any of them: `SmallImageLayoutHelper`/`LargeImageLayoutHelper` hand the image the
 * slot's central square (`LayoutUtils.getCentralSquare`, unconditional) and `RoundedDrawable`
 * centre-crops the image into it, so a wide image loses its sides and a wide slot goes half empty.
 */
internal enum class ComplicationImageFit {

    /** Whole image, aspect kept, centred - letterboxed if the slot is a different shape. Default. */
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
 * Renderer for a complication slot, existing to work around two androidx bugs and to add the image
 * fit the library has no setting for. Nothing here is specific to any watch face, so every watch face
 * hosting slots should use this rather than [CanvasComplicationDrawable] directly.
 *
 * **1. It forces the *synchronous* branch of `ComplicationDrawable.setComplicationData`.** With
 * `loadDrawablesAsync = true` (what the framework passes) the library does not put the new data into
 * the renderer it is currently drawing with. It builds a *second* `ComplicationRenderer` and installs
 * it only once the async drawable load completes - its own comment says this "causes it to render as
 * blank until the async load has completed". On the engine-start / complication-cache-restore path
 * that completion does not arrive, so the live renderer keeps its empty data and paints nothing at
 * all, while `ComplicationDrawable.complicationData` already reports the real type. That combination
 * is exactly the observed symptom: a complication that responds to taps (tap handling reads the slot,
 * not the renderer) and reports correct data at every layer, yet stays invisible until the provider is
 * re-picked. Loading synchronously puts the data into the live renderer immediately; the cost is
 * decoding two or three small complication icons on the calling thread instead of off it.
 *
 * **2. It drops a redundant small image** - see [withoutRedundantSmallImage].
 *
 * **3. It applies [ComplicationImageFit]** - see [render].
 *
 * @param imageFit read per frame rather than passed once, so a watch face can change it whenever its
 *   configuration changes without rebuilding the slot.
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
     * Ignores [loadDrawablesAsynchronous] and always loads synchronously - do not "simplify" this by
     * passing the parameter through, that reintroduces the blank-until-re-picked bug. See the class
     * doc for the full mechanism.
     */
    override fun loadData(complicationData: ComplicationData, loadDrawablesAsynchronous: Boolean) {
        receivedData = complicationData
        super.loadData(prepared(complicationData), false)
    }

    /**
     * Re-runs [prepared] on the data already received, for when the watch face's answer to
     * [iconColorRequested] changes - a new configuration is loaded, say.
     *
     * Needed because the decision is taken in [loadData], which the system only calls when *it* has
     * new data. Starting again from [receivedData] rather than from the loaded data is what makes the
     * change reversible: a dropped small image would otherwise be gone for good.
     */
    fun reapplyIconColorRequest() {
        receivedData?.let { super.loadData(prepared(it), false) }
    }

    private fun prepared(data: ComplicationData) = withoutRedundantSmallImage(data)

    /**
     * Draws the complication, taking over the image itself for the two image-only types so that
     * [ComplicationImageFit] can mean what it says.
     *
     * **Why the image cannot be left to the library.** `SmallImageLayoutHelper` gives it the slot's
     * central square and `RoundedDrawable.drawableToBitmap` centre-crops the image into that square,
     * both unconditionally. The crop happens *before* anything a caller can influence, so no bounds
     * trick and no canvas transform can recover the lost sides: measured on a 300x115 px slot with a
     * 450x225 provider image, the library shows the middle half of the image at 0.51 scale, and
     * scaling that square back out to the slot magnifies it to 1.33x horizontally against 0.51
     * vertically - a wide image comes out cropped *and* distorted. Drawing it here instead uses the
     * image's real intrinsic size, so all three fits are exact.
     *
     * Everything else still comes from `ComplicationDrawable`: this only replaces the image, and only
     * for `SMALL_IMAGE`/`PHOTO_IMAGE`, which carry no text fields. Any other type keeps the library's
     * layout untouched - it places text, and reshaping it would ruin that.
     *
     * Background and rounded corners are drawn here to match, from the same [ComplicationStyle] the
     * library would have used, so `borderRadius` still shapes the slot and clips the image.
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
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RectF(bounds), radius, radius, Path.Direction.CW) })
        if (style.backgroundColor != Color.TRANSPARENT) canvas.drawColor(style.backgroundColor)
        image.bounds = imageFit().destination(image.intrinsicWidth, image.intrinsicHeight, bounds)
        image.draw(canvas)
        canvas.restore()
    }

    /**
     * The image this complication carries, or null when it carries none - which is every type this
     * class leaves to the library.
     *
     * Loading an [Icon] allocates and decodes, so the result is kept until the icon itself changes.
     * Identity comparison is right here: data is only reloaded when the system sends new data, so the
     * same payload keeps the same `Icon` instance.
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
     * Works around an androidx layout bug that drops **both** images when a ranged-value-family
     * complication carries a monochromatic image *and* a small image on a non-wide slot.
     *
     * `RangedValueLayoutHelper.getIconBounds` returns empty whenever a small image exists (the small
     * image is meant to win), but its `getSmallImageBounds` then resolves the small image's position by
     * delegating to `ShortTextLayoutHelper.getIconBounds`, which returns empty for that very same
     * reason. Both rects end up empty and nothing is drawn - confirmed against the 1.2.1 sources and on
     * device, where a provider sending both images rendered ring + value but no icon, while the
     * identical payload under SHORT_TEXT (which never enters that delegation) did show one. Details in
     * `_docs/Complication_Libraries.md`.
     *
     * Dropping the small image makes `hasSmallImage()` false, so the monochromatic image renders
     * normally. Only touched when there is a monochromatic image to fall back on.
     *
     * **The same edit also serves a second, deliberate purpose: honouring a requested icon colour.**
     * `iconColor` reaches only `drawIcon`, i.e. only a `MonochromaticImage`
     * (`ComplicationRenderer.PaintSet` builds `mIconColorFilter` from `style.iconColor` and `drawIcon`
     * applies it with no null branch). Every layout helper prefers a small image when the provider
     * sends both - `ShortTextLayoutHelper.getIconBounds` empties the icon rect on `hasSmallImage()` -
     * and `drawSmallImage` explicitly clears the colour filter for `IMAGE_STYLE_ICON`. So a provider
     * sending both images renders an untintable one, and no style value can change it: Samsung's heart
     * rate icon stays red whatever `iconColor` says. Dropping the small image when - and only when -
     * the watch face actually asked for an icon colour puts the tintable image back in play, for any
     * complication type. Ask for nothing and the provider's own colours are kept, which is the better
     * default and what the library does on its own.
     *
     * The wire round-trip is used rather than `RangedValueComplicationData.Builder` because the builder
     * exposes no setters for `dataSource`, `persistencePolicy`, `displayPolicy` or `extras`, so
     * rebuilding through it would silently discard them; copying the wire object preserves every field
     * and changes exactly one. Both conversions are `@RestrictTo` (lint-only, not private), hence the
     * suppression.
     */
    @Suppress("RestrictedApi")
    private fun withoutRedundantSmallImage(complicationData: ComplicationData): ComplicationData {
        val wire = complicationData.asWireComplicationData()
        // Wire type constants rather than the ComplicationType enum: GOAL_PROGRESS and
        // WEIGHTED_ELEMENTS are @RequiresApi(TIRAMISU) on the enum, while these are plain ints.
        val routedThroughRangedValueLayout =
            wire.type == WireComplicationData.TYPE_RANGED_VALUE ||
                wire.type == WireComplicationData.TYPE_GOAL_PROGRESS ||
                wire.type == WireComplicationData.TYPE_WEIGHTED_ELEMENTS
        // Nothing to fall back on, or nothing to gain: leave the payload exactly as it arrived.
        if (!wire.hasSmallImage() || !wire.hasIcon()) return complicationData
        if (!routedThroughRangedValueLayout && !iconColorRequested()) return complicationData
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
 * A value object rather than a set of mutable fields, for three reasons: the watch face decides every
 * value in one place, "nothing declared" is a single named instance ([LIBRARY_DEFAULTS]) instead of a
 * default repeated at each field, and equality comes for free - which is what a future per-frame
 * re-apply guard needs, since every `ComplicationStyle` setter marks the style dirty.
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
     * Left to the caller rather than forced here: whether a complication draws a border is a design
     * decision, not plumbing. Note androidx defaults it to `BORDER_STYLE_SOLID` with a 1px border no
     * watch face asked for, so the default below is the useful one rather than the library's.
     */
    val borderStyle: Int = ComplicationStyle.BORDER_STYLE_NONE
) {

    /**
     * Writes every value into both style slots of [drawable].
     *
     * **Every property is written on every call, never conditionally.** A `ComplicationDrawable`
     * belongs to the engine and so outlives the watch-face configuration that last styled it; a
     * skipped write silently keeps the previous configuration's value, which is why "not declared"
     * must already have been resolved to a real value by the caller.
     *
     * `borderRadius` is worth knowing about: androidx defaults it to `Int.MAX_VALUE`, which
     * `ComplicationRenderer` clamps to half the shorter edge and then uses to inset the *content* area
     * by `ceil((sqrt(2)-1) * radius)` on every side - throwing away ~41% of the declared box before any
     * text or icon is placed. It also shapes the background fill (a round rect) and, when a border is
     * drawn, the border itself.
     */
    fun applyTo(drawable: ComplicationDrawable) {
        for (style in listOf(drawable.activeStyle, drawable.ambientStyle)) {
            style.borderStyle = borderStyle
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
         * "As large as the box allows" for `textSize`/`titleSize`.
         *
         * Both are *upper bounds*, not exact sizes: `ComplicationRenderer` hands them to
         * `Paint.setTextSize` and `TextRenderer` then shrinks in steps until the text fits. This is
         * androidx's own field-initializer value and therefore its intended "no opinion" marker - but
         * note it is **not** what a context-attached drawable starts with, since
         * `setStyleToDefaultValues` overwrites it from resources with 14sp. Writing it explicitly is
         * what lets complication text use its whole box instead of being capped at 14sp.
         */
        const val TEXT_SIZE_FIT_BOX = Int.MAX_VALUE

        /**
         * Mirrors androidx's *resource* default for `rangedValueSecondaryColor`
         * (`R.color.complicationDrawable_rangedValueSecondaryColor` = 50% white), so a watch face that
         * declares no ring colours keeps the appearance it had before it styled them. The field
         * initializer says `Color.LTGRAY`, which is not what a context-attached drawable uses - see the
         * resource-defaults warning in `_docs/Complication_Libraries.md`.
         */
        const val RING_SECONDARY_COLOR_DEFAULT = 0x80FFFFFF.toInt()

        /**
         * What a slot looks like before any watch-face styling has been decoded - a drawable can be
         * created before that happens, so this is what it gets in the meantime.
         */
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
            ringSecondaryColor = RING_SECONDARY_COLOR_DEFAULT
        )
    }
}

/**
 * Live state for one complication slot, scoped to one engine instance.
 *
 * [drawable] is why this cannot be static or enum state: it owns a `ComplicationRenderer` and an
 * invalidate callback bound to the engine that created it, and a watch-face editor runs a second,
 * headless instance of the same watch face concurrently.
 *
 * [style] and [drawable] are filled by whichever of the two orderings the framework happens to use -
 * the watch face's configuration may be decoded before the drawable exists (renderers are created
 * lazily) or after it - so [applyStyle] is called from both paths and is idempotent.
 */
internal class ComplicationSlotState(val slotId: Int) {

    var drawable: ComplicationDrawable? = null

    var style: ComplicationStyleValues = ComplicationStyleValues.LIBRARY_DEFAULTS

    /**
     * How an image-only complication fills this slot. Read on the render thread every frame, so no
     * push into the drawable is needed - unlike [style], nothing in `ComplicationDrawable` holds it.
     */
    @Volatile var imageFit: ComplicationImageFit = ComplicationImageFit.FIT_CENTER

    /** Set by [WatchFaceComplications] when the slot's renderer is created. */
    internal var renderer: SyncLoadingCanvasComplication? = null

    /**
     * Whether the watch face asked for a specific icon colour on this slot, which decides whether a
     * provider's own small image may be dropped in favour of the tintable monochromatic one - see
     * `SyncLoadingCanvasComplication.withoutRedundantSmallImage`.
     *
     * Setting it re-runs that decision on the data already received, because the system only calls
     * `loadData` when *it* has something new, and a configuration change is not that.
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
 * Deliberately knows nothing about any watch face: geometry arrives through lambdas, style through
 * [ComplicationSlotState.style], and the user-visible labels through resource ids passed in. That is
 * what makes it reusable by a watch face other than the one using it today.
 *
 * @param slotIds the slots to host, in the watch face's own order
 * @param supportedTypes complication types this watch face accepts, **in preference order** - during
 *   data source selection the system walks the list and picks the first entry a provider also
 *   supports (`ComplicationSlot.kt`'s `supportedTypes` doc, confirmed against source; see
 *   `_docs/Complication_Libraries.md`, "Supported-type negotiation")
 * @param layoutSettingLabel label for the slot-layout user-style setting, shown by a system editor
 * @param layoutSettingDescription its description
 */
internal class WatchFaceComplications(
    private val context: Context,
    private val slotIds: List<Int>,
    private val supportedTypes: List<ComplicationType>,
    @StringRes private val layoutSettingLabel: Int,
    @StringRes private val layoutSettingDescription: Int
) {

    companion object {

        private const val SLOT_STYLE_SETTING_ID = "wf_slots"
        private const val SLOT_STYLE_OPTION_ID_DEFAULT = "wf_d"
        private const val SLOT_STYLE_OPTION_ID_PREFIX = "wf_"
    }

    private val slotStates: Map<Int, ComplicationSlotState> = slotIds.associateWith { ComplicationSlotState(it) }

    // Created on a background thread in createUserStyleSchema()/createSlotsManager(), read on the
    // render thread in syncGeometry() - hence @Volatile.
    @Volatile private var slotsSetting: ComplicationSlotsUserStyleSetting? = null
    @Volatile private var userStyleRepository: CurrentUserStyleRepository? = null
    @Volatile private var slotsManager: ComplicationSlotsManager? = null

    // Keeps every pushed option's id distinct - see syncGeometry() for why that matters.
    private var geometryRevision = 0

    /** The live state for [slotId], for a watch face to write its decoded style into. */
    fun stateFor(slotId: Int): ComplicationSlotState? = slotStates[slotId]

    /**
     * Declares the one [ComplicationSlotsUserStyleSetting] that [syncGeometry] needs in order to move
     * the slots at runtime. A watch face whose slot geometry never changes does not need to call this.
     *
     * The single option carries **no** overlays, which the library documents as meaning "the net result
     * is the initial complication configuration" - so by itself this schema changes nothing. It exists
     * only because `CurrentUserStyleRepository.updateUserStyle` validates that the *setting* is part of
     * the schema; the options actually applied are built later, at runtime.
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
     * Everything decided here is **creation-time only**: `ComplicationSlot.Builder` arguments are fixed
     * once built, and a slot's bounds and enabled flag can afterwards be changed solely through
     * [syncGeometry]'s user-style route. A default data source policy or a fixed data source would also
     * have to be supplied here rather than later.
     */
    fun createSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository,
        initialBoundsFor: (slotId: Int) -> ComplicationSlotBounds
    ): ComplicationSlotsManager {
        // Both kept so syncGeometry() can push slot geometry changes back through the repository, and
        // read back what the slots actually ended up with.
        userStyleRepository = currentUserStyleRepository
        val slots = slotStates.values.map { state -> buildSlot(state, initialBoundsFor(state.slotId)) }
        return ComplicationSlotsManager(slots, currentUserStyleRepository).also { slotsManager = it }
    }

    private fun buildSlot(state: ComplicationSlotState, bounds: ComplicationSlotBounds): ComplicationSlot {
        val canvasComplicationFactory = CanvasComplicationFactory { watchState, invalidateCallback ->
            // The drawable is kept on the state so styling never depends on being able to reach it
            // through ComplicationSlotsManager. The library creates renderers lazily, so this can run
            // either before or after the watch face has decoded its style; applying here covers
            // "drawable created last", and the watch face's own decode pass covers "style decoded
            // last".
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
            supportedTypes = supportedTypes,
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = bounds
        ).build()
    }

    /**
     * Brings each slot's **declared** bounds and enabled state into line with what is actually being
     * drawn, which is what fixes complication *taps* after the watch face moves a slot.
     *
     * [visibleBoundsFor] returns fractional, unit-square (canvas-relative) bounds for a slot, or null
     * when it is currently hidden - in which case the slot is pushed as disabled with empty bounds,
     * which is what stops a hidden complication from receiving taps or system-side data at all.
     *
     * Painting a complication elsewhere (see [ComplicationRender]) only fixes what the user sees: tap
     * hit-testing goes through `ComplicationSlotsManager.getComplicationSlotAt` ->
     * `RoundRectComplicationTapFilter` -> `ComplicationSlot.computeBounds`, which reads the slot's own
     * cached bounds and never the ones a render call was given. Left alone the two diverge the moment a
     * watch face moves a complication - confirmed on device: with two configurations differing only by
     * two slots being swapped, each rendered in its new place but tapping it fired the *other* slot's
     * provider, silently and wrongly.
     *
     * `ComplicationSlot.complicationSlotBounds` and `.enabled` have `internal` setters, so the only
     * supported way to write them is a user-style change: the library's own
     * `ComplicationSlotsManager.listenForStyleChanges` reacts to a new [ComplicationSlotsOption] by
     * calling `applyComplicationSlotsStyleCategoryOption`, which writes both. That also keeps
     * accessibility bounds and what an editor sees correct, which a render-side-only fix cannot.
     *
     * Two non-obvious constraints, both verified against the androidx sources:
     * - **Every push needs a fresh option id.** `UserStyleSetting.Option.equals` compares *only* the
     *   id, and `listenForStyleChanges` ignores an option it considers equal to the previous one.
     *   Reusing an id means new bounds are accepted and then silently never applied.
     * - `updateUserStyle` is `@RestrictTo(LIBRARY_GROUP)`, hence the suppression. It is lint-only - the
     *   call is ordinary type-checked Kotlin, so a future incompatible change breaks the build rather
     *   than failing silently at runtime. `validateUserStyle` requires only that the setting belongs to
     *   the schema and that the option's class matches it; it deliberately does *not* require the
     *   option to be one the schema enumerated, which is what makes runtime-computed bounds legal.
     *
     * Safe to call once per frame: it pushes only when a slot is not already where it should be.
     *
     * **It compares against the slots themselves, not against what was last pushed**, and that is
     * deliberate. `applyComplicationSlotsStyleCategoryOption`
     * (`ComplicationSlotsManager.kt:225-239`) restores a slot's *initial* bounds and enabled flag
     * whenever an applied option carries no overlay for it - and the schema's own default option
     * carries none. So a style change from anywhere else silently resets slots this class is
     * responsible for. Comparing intent to intent would then never notice: the wanted geometry has
     * not changed, only the slot has, and the watch face would keep drawing complications correctly
     * at coordinates the tap filter no longer knows about - visible only as taps that do nothing.
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
            // Builder rather than the constructor: the overlay's 4-argument secondary constructor is
            // deprecated, and named arguments resolve to it rather than to the primary one.
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
