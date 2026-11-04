package app.aaps.wear.complications.cwf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.watchfaces.CustomWatchface
import java.io.ByteArrayOutputStream

/**
 * Builds the picture of the Custom watch face, one finished frame at a time.
 *
 * The face is drawn once into layers, cut wherever the refresh rate changes (see
 * [CustomWatchface.RenderLayer]). Slow layers are kept as bitmaps; only the two that carry the clock
 * are redrawn per frame, and the finished frame is the layers blended back in paint order.
 *
 * Why this shape, from what a Galaxy Watch 4 measured (`_docs/CWF_WFF_Prompt.md` sections 10.4-10.6):
 *
 * - Blending a cached layer costs about **1 ms**, while redrawing the views it holds costs 3 to 31 ms
 *   for a hand and about 30 ms for a full-size image. So cutting finely is nearly free and caching
 *   pays on every frame.
 * - Re-reading the repository costs **77 ms**, and a clock tick has no reason to do it: the second
 *   changed, not the glucose. The data is read when it changes, and never on a tick.
 * - WEBP lossless beats PNG on both time and size, everywhere: 82 ms against 126 ms and 45 kB against
 *   68 kB at full screen. It is lossless, so nothing is given up.
 *
 * Together those take a frame from about 340 ms to about 100 ms.
 */
class CwfFramePipeline(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val watchFaceProvider: () -> CwfRenderTarget
) {

    companion object {

        /** Starting size for the compression buffer - only avoids a few reallocations. */
        private const val COMPRESS_HINT_BYTES = 64 * 1024
    }

    /** The cached layers, by layer. Only non-[CustomWatchface.RenderLayer.Refresh.TICK] layers appear. */
    private val cache = mutableMapOf<CustomWatchface.RenderLayer, Bitmap>()

    /** Set when the data changed, so the next frame rebuilds everything the data can reach. */
    @Volatile private var dataStale = true

    /**
     * What the cached layers were built from, or null while nothing is cached.
     *
     * Compared rather than invalidated from outside - see [CwfCachePolicy]. The layers held here are
     * the same in both modes: the only thing ambient changes in this render path is whether the
     * seconds are drawn, and those live in the two layers that are never cached.
     */
    private var cached: CachedLayers? = null

    /** How long the last frame took to draw and to compress. Read by the pacing logic. */
    @Volatile var lastFrameMs: Long = 0
        private set

    @Volatile private var lastComposeMs: Long = 0

    /**
     * Whether the frame being built had to rebuild layers.
     *
     * A rebuild frame is not a typical frame: it happens on new data, on a new zip and once a minute,
     * costs several hundred milliseconds, and would otherwise be read by the pacing logic as the speed
     * of this watch - which once made the clock drop to its five second interval and sleep there while
     * the wearer was looking at it.
     */
    @Volatile private var rebuiltThisFrame = false

    /** Marks every cached layer stale. Called when new data arrives, or a preference changes. */
    fun invalidateData() {
        dataStale = true
    }

    /** Drops the cached layers, for a change that could alter the layout itself. */
    fun invalidateAll() {
        dataStale = true
        cached = null
    }

    /**
     * Builds the finished picture for [instant], as encoded bytes.
     *
     * Rebuilds only what is stale: everything when the data changed, the minute layers when the
     * minute turned, and the two clock layers always. The watch face is asked to draw for [instant]
     * rather than for now, so a frame prepared ahead of time carries the clock of the second it will
     * be shown in.
     */
    fun compose(
        instant: Long,
        ambient: Boolean,
        width: Int,
        height: Int,
        ambientAtDraw: () -> Boolean = { ambient }
    ): Bitmap {
        val started = System.currentTimeMillis()
        val watchFace = watchFaceProvider()
        try {
            // The preconditions of a frame, named, in the order they have to happen. Each one is a
            // fault that reached a wrist before it was written down here - see [CwfRenderTarget].
            viewsExist(watchFace, width, height)
            modeIs(watchFace, ambient)
            instantIs(watchFace, instant)
            secondsFollowTheMode(watchFace)
            clockShowsTheInstant(watchFace)
            dataIsFreshEnough(watchFace, instant, width, height)
            secondsFollowTheModeAgainIfItChanged(watchFace, ambient, ambientAtDraw)
            return stack(watchFace, width, height).also { lastComposeMs = System.currentTimeMillis() - started }
        } finally {
            // Left as "now" and "follow the mode" for anything that draws outside this pipeline -
            // the preview, the editor, and the live watch face itself
            watchFace.setRenderInstant(null)
            watchFace.setRenderSeconds(null)
        }
    }

    /**
     * The views exist and are laid out for this size.
     *
     * First, always: everything after this touches the view hierarchy. A frame that skipped it
     * raised `UninitializedPropertyAccessException`, and nine frames were lost in thirty seconds.
     */
    private fun viewsExist(watchFace: CwfRenderTarget, width: Int, height: Int) =
        watchFace.prepareLayout(width, height)

    /**
     * The face is drawing for this mode.
     *
     * Set once, before anything reads it, and never changed again inside a frame: the mode also
     * chooses the simple always-on display, so a late change could empty the picture.
     */
    private fun modeIs(watchFace: CwfRenderTarget, ambient: Boolean) =
        watchFace.setRenderAmbient(ambient)

    /** The face is drawing for the moment this frame will be shown, not for now. */
    private fun instantIs(watchFace: CwfRenderTarget, instant: Long) =
        watchFace.setRenderInstant(instant)

    /**
     * The seconds are shown or hidden according to the design and the mode.
     *
     * Nothing else does this for a render-only face: the live watch face is told by
     * `onWatchModeChanged`, and the only other caller sits inside the data reload, which most frames
     * no longer perform. Leaving it out left the second hand hidden for good.
     */
    private fun secondsFollowTheMode(watchFace: CwfRenderTarget) =
        watchFace.updateSecondVisibility()

    /**
     * The clock is at the instant being drawn - hands and time fields together.
     *
     * **Before the cached layers are drawn, and that order is the point.** The cached layers are
     * drawn from these same views, so rebuilding them first froze the hour and minute hands at their
     * previous positions for a whole minute while the second hand, drawn afterwards, stayed right.
     */
    private fun clockShowsTheInstant(watchFace: CwfRenderTarget) =
        watchFace.setSecond()

    /** The cached layers match the current data, design and minute - see [refreshCaches]. */
    private fun dataIsFreshEnough(watchFace: CwfRenderTarget, instant: Long, width: Int, height: Int) =
        refreshCaches(watchFace, instant, width, height)

    /**
     * The seconds still follow the mode at the moment of drawing, not the moment of starting.
     *
     * Last, because everything expensive is behind us and the two layers carrying the seconds have
     * still not been drawn. A frame takes 200 ms to over a second, and the wrist can drop in that
     * time; the frame then went out with its seconds on it and the runtime painted it on a dimmed
     * screen. Only the seconds are taken back, never the mode - see [modeIs]. The cached layers are
     * untouched and stay valid, because they are the same in both modes.
     */
    private fun secondsFollowTheModeAgainIfItChanged(
        watchFace: CwfRenderTarget,
        ambient: Boolean,
        ambientAtDraw: () -> Boolean
    ) {
        if (!ambientAtDraw() || ambient) return
        watchFace.setRenderSeconds(false)
        watchFace.updateSecondVisibility()
        watchFace.setSecond()
        aapsLogger.debug(LTag.WEAR, "CwfFramePipeline: watch dozed while drawing, seconds left out")
    }

    /**
     * Rebuilds the cached layers that have gone stale.
     *
     * The data reaches views in several layers, including the glucose and the trend arrow which sit
     * in the middle of the stack, so a data change rebuilds every cached layer rather than trying to
     * work out which ones it touched. That costs 15 to 100 ms and happens once every five minutes.
     *
     * The data is read here, and only here. This is the one place a frame is allowed to pay the
     * 77 ms the repository costs.
     */
    private fun refreshCaches(watchFace: CwfRenderTarget, instant: Long, width: Int, height: Int) {
        val minute = instant / 60_000
        val toRebuild = CwfCachePolicy.toRebuild(watchFace.styleId, minute, cached, dataStale)
        rebuiltThisFrame = toRebuild.isNotEmpty()
        if (toRebuild.isEmpty()) return

        // Only a data rebuild reloads. A minute turning moves the hands; it does not change a reading.
        if (toRebuild.contains(CustomWatchface.RenderLayer.Refresh.DATA)) {
            watchFace.refreshRenderData()
            dataStale = false
        }

        CustomWatchface.RenderLayer.entries
            .filter { it.refresh in toRebuild }
            .forEach { layer ->
                cache.remove(layer)?.recycle()
                cache[layer] = watchFace.renderLayer(width, height, layer)
            }
        // Recorded after the rebuild, so it carries the style the layers were actually drawn with -
        // refreshRenderData above may have applied a newly sent zip
        cached = CachedLayers(watchFace.styleId, minute)
        aapsLogger.debug(LTag.WEAR, "CwfFramePipeline: rebuilt $toRebuild (style ${watchFace.styleId})")
    }

    /** Stacks the cached layers and the two clock layers back together, in paint order. */
    private fun stack(watchFace: CwfRenderTarget, width: Int, height: Int): Bitmap {
        val target = createBitmap(width, height)
        val canvas = Canvas(target)
        CustomWatchface.RenderLayer.entries.forEach { layer ->
            if (layer.refresh == CustomWatchface.RenderLayer.Refresh.TICK) {
                val drawn = watchFace.renderLayer(width, height, layer)
                canvas.drawBitmap(drawn, 0f, 0f, null)
                drawn.recycle()
            } else {
                cache[layer]?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            }
        }
        return target
    }

    /**
     * Compresses a frame, and releases it. Safe off the main thread - it only reads pixels - and that
     * is where the callers run it, because compression is the single most expensive step.
     *
     * WEBP lossless rather than PNG: measured on a Galaxy Watch 4 it is faster **and** smaller, at
     * 82 ms and 45 kB against 126 ms and 68 kB for the same picture. WEBP *lossy* is slower than
     * lossless on this hardware, so it is not worth the quality it would cost.
     *
     * Compressed rather than sent raw because a raw 450x450 frame is 791 kB, and shipping that across
     * Binder every second had the system killing the process.
     */
    fun encode(bitmap: Bitmap): ByteArray {
        val started = System.currentTimeMillis()
        val stream = ByteArrayOutputStream(COMPRESS_HINT_BYTES)
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
        bitmap.recycle()
        // Only a steady frame sets the pace; see [rebuiltThisFrame]
        if (!rebuiltThisFrame) lastFrameMs = lastComposeMs + (System.currentTimeMillis() - started)
        return stream.toByteArray()
    }

    /** Releases every cached layer. */
    fun release() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        invalidateAll()
    }
}
