package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;
import android.os.Parcel;

import com.gxwtech.roundtrip2.RT2Const;

/**
 * Created by geoff on 7/6/16.
 *
 * This class exists to hold a ServiceCommand along with transport variables
 * such as time sent, time received, sender.
 * May also contain result, if the command is completed.
 */
public class ServiceTransport extends ServiceMessage {
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
        map.putString("ServiceMessageType","ServiceTransport");
        setTransportType("unknown");
        setSenderHashcode(0);
    }

    public void setSenderHashcode(Integer senderHashcode) {
        map.putInt("senderHashcode",senderHashcode);
    }

    public Integer getSenderHashcode() {
        return new Integer(map.getInt("senderHashCode",0));
    }

    public void setServiceCommand(ServiceCommand serviceCommand) {
        map.putBundle("ServiceCommand",serviceCommand.getMap());
    }

    public ServiceCommand getServiceCommand() {
        return new ServiceCommand(map.getBundle("ServiceCommand"));
    }

    public boolean hasServiceCommand() {
        return (getMap().containsKey("ServiceCommand"));
    }

    // On remote end, this will be converted to the "action" of a local Intent,
    // so can be used for separating types of messages to different internal handlers.
    public void setTransportType(String transportType) {
        map.putString("transportType",transportType);
    }

    public String getTransportType() {
        return map.getString("transportType","unknown");
    }

    public void setServiceResult(ServiceResult serviceResult) {
        map.putBundle("ServiceResult",serviceResult.getMap());
    }

    public ServiceResult getServiceResult() {
        return new ServiceResult(map.getBundle("ServiceResult"));
    }

    public boolean hasServiceResult() {
        return (getMap().containsKey("ServiceResult"));
    }

    public void setServiceNotification(ServiceNotification notification) {
        map.putBundle("ServiceNotification",notification.getMap());
    }

    public ServiceNotification getServiceNotification() {
        return new ServiceNotification(map.getBundle("ServiceNotification"));
    }

    public boolean hasServiceNotification() {
        return (getMap().containsKey("ServiceNotification"));
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
        if (RT2Const.IPC.MSG_ServiceNotification.equals(getTransportType())) {
            rval += "note: " + getServiceNotification().getNotificationType();
        } else if (RT2Const.IPC.MSG_ServiceCommand.equals(getTransportType())) {
            rval += ", cmd=" + getOriginalCommandName();
        } else if (RT2Const.IPC.MSG_ServiceResult.equals(getTransportType())) {
            rval += ", cmd=" + getOriginalCommandName();
            rval += ", rslt=" + getServiceResult().getResult();
            rval += ", err=" + getServiceResult().getErrorDescription();
        }
        return rval;
    }

    public ServiceTransport clone() {
        Parcel p = Parcel.obtain();
        Parcel p2 = Parcel.obtain();
        getMap().writeToParcel(p,0);
        byte[] bytes = p.marshall();
        p2.unmarshall(bytes, 0, bytes.length);
        p2.setDataPosition(0);
        Bundle b = p2.readBundle();
        ServiceTransport rval = new ServiceTransport();
        rval.setMap(b);
        return rval;
    }
}
