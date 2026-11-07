package app.aaps.wear.complications.cwf

import app.aaps.wear.R

/**
 * The Custom watch face drawn **without seconds**, for the always-on slot of the pushed document.
 *
 * The document holds two full-screen pictures of the same face - this one and [CwfFaceComplication]'s
 * - and swaps them itself when the watch dozes. That swap is the whole reason this exists: the
 * runtime repaints once as the screen dims and then not again until the minute turns, and that one
 * repaint takes the picture it already has. Delivering a secondless picture 154 ms later, which is
 * the best our own detection can do, is 154 ms too late. Letting the runtime choose between two
 * pictures it already holds is not a race at all.
 *
 * It costs very little. Everything expensive is shared with [CwfFaceComplication] through that
 * class's companion - one watch face, one pipeline, one set of cached layers, one read of the data -
 * and the picture itself is only redrawn when the minute changes. A zip with seconds switched off
 * makes the two pictures identical, and then this slot adds no drawing at all.
 *
 * See [CwfMinuteFrame] for the measurements, and for why the seconds could not simply be lifted into
 * a layer of their own.
 */
class CwfAmbientFaceComplication : CwfFaceComplication() {

    override val label = R.string.label_watchface_custom_ambient
    override val withoutSeconds = true
    override fun getProviderCanonicalName(): String = CwfAmbientFaceComplication::class.java.canonicalName!!
}
