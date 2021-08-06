package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

abstract class ActivationResponseBase extends ResponseBase {
    final ResponseType.ActivationResponseType activationResponseType;

    ActivationResponseBase(ResponseType.ActivationResponseType activationResponseType, byte[] encoded) {
        super(ResponseType.ACTIVATION_RESPONSE, encoded);
        this.activationResponseType = activationResponseType;
    }

    public ResponseType.ActivationResponseType getActivationResponseType() {
        return activationResponseType;
    }
}
