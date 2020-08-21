package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum PodResponseType {

    Acknowledgment, // set commands would just acknowledge if data was sent
    Data, // query commands would return data
    Error, // communication/response produced an error
    Invalid; // invalid response (not supported, should never be returned)

    public boolean isError() {
        return this == Error || this == Invalid;
    }
}
