package app.aaps.core.keys

/**
 * Values of [StringKey.WearPushedWatchface]: the id of the embedded Watch Face Format face the wear
 * app installs through Watch Face Push. The id is also the face's package suffix
 * (`<wear app>.watchfacepush.<id>`) and the name of its embedded asset, so the phone and the watch
 * must agree on it - which is why it lives here and not in either of them.
 */
object PushedWatchfaceId {

    /** The face designed in Watch Face Studio: a layout of AAPS complications */
    const val WFS = "wfs"

    /** The face that shows the wearer's own Custom watchface zip as a picture */
    const val CWF = "cwf"
}
