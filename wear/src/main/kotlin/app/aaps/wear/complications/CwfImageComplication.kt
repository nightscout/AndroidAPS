package app.aaps.wear.complications

import app.aaps.wear.complications.cwf.CwfFaceComplication

import app.aaps.wear.R

/**
 * The whole Custom watch face as one image, offered under its own name.
 *
 * Identical in behaviour to [CwfFaceComplication], which the watch face AAPS pushes uses. This one
 * exists so that any *other* watch face with an image slot can pick "Custom Watchface image" from the
 * complication list and get the same picture. Separate rather than shared because a data source is
 * addressed by its class name: dropping it would silently empty the slot for anyone who had already
 * chosen it.
 */
class CwfImageComplication : CwfFaceComplication() {

    override val label = R.string.complication_cwf_image
    override fun getProviderCanonicalName(): String = CwfImageComplication::class.java.canonicalName!!
}
