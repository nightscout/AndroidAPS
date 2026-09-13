package app.aaps.wear.complications

import app.aaps.wear.complications.cwf.CwfFaceComplication

import app.aaps.wear.R

/**
 * The whole Custom watch face as one image, under its own name.
 *
 * Identical in behaviour to [CwfFaceComplication], which the watch face AAPS pushes uses. This one
 * used to be offered in the complication chooser, so that any *other* watch face with an image slot
 * could pick "Custom Watchface" and get the same picture. It is hidden from the chooser now, like its
 * two siblings: AAPS ships the face that shows the picture, and three near-identical entries there
 * looked like a mistake. Kept rather than dropped because a data source is addressed by its class
 * name: a slot that already holds it keeps working, and dropping the class would silently empty it.
 */
class CwfImageComplication : CwfFaceComplication() {

    override val label = R.string.complication_cwf_image
    override fun getProviderCanonicalName(): String = CwfImageComplication::class.java.canonicalName!!
}
