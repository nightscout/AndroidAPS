package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data;

import android.os.Bundle;
import android.os.Parcel;

/**
 * Created by geoff on 7/6/16.
 * <p>
 * This class exists to hold a ServiceCommand along with transport variables such as time sent, time received, sender.
 * May also contain result, if the command is completed.
 */
public class ServiceTransport extends ServiceMessage {

    private ServiceTransportType serviceTransportType = ServiceTransportType.Undefined;


    public ServiceTransport() {
    }


    public ServiceTransport(Bundle b) {
        if (b != null) {
            if ("ServiceTransport".equals(b.getString("ServiceMessageType"))) {
                setMap(b);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }


    @Override
    public void init() {
        super.init();
        map.putString("ServiceMessageType", "ServiceTransport");
        setTransportType("unknown");
        setSenderHashcode(0);
    }


    public Integer getSenderHashcode() {
        return map.getInt("senderHashCode", 0);
    }


    public void setSenderHashcode(Integer senderHashcode) {
        map.putInt("senderHashcode", senderHashcode);
    }


    public ServiceCommand getServiceCommand() {
        return new ServiceCommand(map.getBundle("ServiceCommand"));
    }


    public void setServiceCommand(ServiceCommand serviceCommand) {
        map.putBundle("ServiceCommand", serviceCommand.getMap());
        this.serviceTransportType = ServiceTransportType.ServiceCommand;
    }


    public boolean hasServiceCommand() {
        return (getMap().containsKey("ServiceCommand"));
    }


    public String getTransportType() {
        return map.getString("transportType", "unknown");
    }


    // On remote end, this will be converted to the "action" of a local Intent,
    // so can be used for separating types of messages to different internal handlers.
    public void setTransportType(String transportType) {
        map.putString("transportType", transportType);
    }


    public ServiceResult getServiceResult() {
        return new ServiceResult(map.getBundle("ServiceResult"));
    }


    public void setServiceResult(ServiceResult serviceResult) {
        map.putBundle("ServiceResult", serviceResult.getMap());
        this.serviceTransportType = ServiceTransportType.ServiceResult;
    }


    public boolean hasServiceResult() {
        return (getMap().containsKey("ServiceResult"));
    }


    public ServiceNotification getServiceNotification() {
        return new ServiceNotification(map.getBundle("ServiceNotification"));
    }


    public void setServiceNotification(ServiceNotification notification) {
        map.putBundle("ServiceNotification", notification.getMap());
        this.serviceTransportType = ServiceTransportType.ServiceNotification;
    }


    public boolean hasServiceNotification() {
        return (map.containsKey("ServiceNotification"));
    }


    public boolean commandDidCompleteOK() {
        return getServiceResult().resultIsOK();
    }


    public String getOriginalCommandName() {
        return getServiceCommand().getCommandName();
    }


    public String describeContentsShort() {
        String rval = "";
        rval += getTransportType();

        if (this.serviceTransportType == ServiceTransportType.ServiceNotification) {
            rval += "note: " + getServiceNotification().getNotificationType();
        } else if (this.serviceTransportType == ServiceTransportType.ServiceCommand) {
            rval += ", cmd=" + getOriginalCommandName();
        } else if (this.serviceTransportType == ServiceTransportType.ServiceResult) {
            rval += ", cmd=" + getOriginalCommandName();
            rval += ", rslt=" + getServiceResult().getResult();
            rval += ", err=" + getServiceResult().getErrorDescription();
        }
        return rval;
    }


    public ServiceTransport clone() {
        Parcel p = Parcel.obtain();
        Parcel p2 = Parcel.obtain();
        getMap().writeToParcel(p, 0);
        byte[] bytes = p.marshall();
        p2.unmarshall(bytes, 0, bytes.length);
        p2.setDataPosition(0);
        Bundle b = p2.readBundle();
        ServiceTransport rval = new ServiceTransport();
        rval.setMap(b);
        return rval;
    }
}
