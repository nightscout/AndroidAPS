package app.aaps.wear.complications.cwf

import android.graphics.Bitmap
import app.aaps.wear.watchfaces.CustomWatchface

/**
 * Everything the pipeline needs from a watch face in order to draw one frame, and nothing else.
 *
 * This exists because of what went wrong without it. Building a frame has preconditions - the views
 * must exist, the mode must be set, the clock must have been moved, the data must be fresh enough -
 * and they used to be satisfied by calls scattered through one long function, in an order nobody had
 * written down. Each call was right on its own; the order was the part that broke. In one day that
 * cost three faults on the watch:
 *
 * - the clock never moved, because the call that moves it was only reached through the data reload,
 *   which most frames skip;
 * - the second hand disappeared for good, because the visibility rule read the value it had just
 *   written;
 * - the hour and minute hands ran a minute late, because the cached layers were drawn *before* the
 *   clock was moved.
 *
 * Naming the steps behind an interface is what makes the order checkable: `CwfFramePipelineTest`
 * records the calls a real frame makes and asserts the sequence, so moving or dropping one fails a
 * test instead of a watch face.
 *
 * Implemented by [CustomWatchface], which is also the live watch face people wear. Nothing here is
 * new behaviour for it - every member already existed for the render path.
 */
interface CwfRenderTarget {

    /** Which design the views carry. A new value means every cached layer belongs to the old one. */
    val styleId: Int

    /**
     * Makes sure the views exist and are laid out for this size.
     *
     * **First, always.** Everything below touches the view hierarchy, and a frame that skipped this
     * raised `UninitializedPropertyAccessException` - nine frames lost in thirty seconds.
     */
    fun prepareLayout(width: Int, height: Int)

    /** Draws for this moment rather than for now. Null puts the clock back on the real time. */
    fun setRenderInstant(millis: Long?)

    /** Draws for this mode. Affects far more than the seconds, so it is never changed mid-frame. */
    fun setRenderAmbient(ambient: Boolean)

    /**
     * Leaves the seconds out of this frame, without touching the mode.
     *
     * Null follows the mode again. Separate from [setRenderAmbient] on purpose: the mode also
     * chooses the simple always-on display, so switching it late would draw an empty picture for
     * anyone using that.
     */
    fun setRenderSeconds(show: Boolean?)

    /** Re-reads the values - glucose, insulin, carbs. The dearest step, so only when they changed. */
    fun refreshRenderData()

    /** Decides whether the seconds are drawn at all, from the design and the mode. */
    fun updateSecondVisibility()

    /** Moves the clock to the instant being drawn - the hands and the time fields together. */
    fun setSecond()

    /** Draws one layer of the face into its own bitmap. */
    fun renderLayer(width: Int, height: Int, layer: CustomWatchface.RenderLayer): Bitmap
}
