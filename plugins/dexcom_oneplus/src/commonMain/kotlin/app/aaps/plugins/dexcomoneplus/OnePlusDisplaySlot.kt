package app.aaps.plugins.dexcomoneplus

/**
 * Which connection slot of the sensor a driver asks for in the auth request.
 *
 * A G7 / ONE+ sensor keeps one slot per display type (the last byte of the auth request), so a
 * phone and a watch can both read it at the same time, each with its own key, but two phones
 * cannot. The phone always takes [PHONE]. [WATCH] is only used by direct-to-watch on Wear OS.
 *
 * The codes are the sensor's display types. They match `G7DisplayType` in nightscout/AndroidAPS
 * pull request 5397 and the byte xDrip's keks library sends for a phone.
 */
enum class OnePlusDisplaySlot(val code: Int) {

    PHONE(2),
    WATCH(3);

    /**
     * The slot byte to force in the KEKS auth request, or null to keep the library's own byte.
     *
     * Null for [PHONE] on purpose: the phone path must stay byte for byte what it always was.
     */
    val authSlotOverride: Int? get() = if (this == PHONE) null else code

    /**
     * True only for the slot whose readings may reach the insulin loop.
     *
     * A watch reading is for the watch faces only. The phone's own reading of the same sensor is
     * the one the loop gets, so a reading from any other slot must never be stored as glucose.
     */
    val mayFeedLoop: Boolean get() = this == PHONE
}
