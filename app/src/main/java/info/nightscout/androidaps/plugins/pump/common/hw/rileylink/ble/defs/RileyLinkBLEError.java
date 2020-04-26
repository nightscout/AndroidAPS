package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

/**
 * Created by andy on 11/24/18.
 */

public enum RileyLinkBLEError {
    CodingErrors("Coding Errors encountered during decode of RileyLink packet."), //
    Timeout("Timeout"), //
    Interrupted("Interrupted"),
    TooShortOrNullResponse("Too short or null decoded response.");

    private String description;


    RileyLinkBLEError(String description) {

        this.description = description;
    }


    public String getDescription() {
        return description;
    }
}
