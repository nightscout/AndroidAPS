package app.aaps.pump.common.hw.rileylink.ble.defs

/**
 * Created by andy on 11/24/18.
 */
enum class RileyLinkBLEError(val description: String) {

    CodingErrors("Coding Errors encountered during decode of RileyLink packet."),  //
    Timeout("Timeout"),  //
    Interrupted("Interrupted"),
    NoResponse("No response from RileyLink"),
    TooShortOrNullResponse("Too short or null decoded response.");
}
