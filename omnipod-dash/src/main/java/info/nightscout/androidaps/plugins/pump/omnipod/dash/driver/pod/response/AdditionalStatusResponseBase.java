package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

public class AdditionalStatusResponseBase extends ResponseBase {
    final ResponseType.AdditionalStatusResponseType statusResponseType;

    AdditionalStatusResponseBase(ResponseType.AdditionalStatusResponseType statusResponseType, byte[] encoded) {
        super(ResponseType.ADDITIONAL_STATUS_RESPONSE, encoded);
        this.statusResponseType = statusResponseType;
    }

    public ResponseType.AdditionalStatusResponseType getStatusResponseType() {
        return statusResponseType;
    }
}
