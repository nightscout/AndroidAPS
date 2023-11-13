/**
 * Copyright (C) 2014 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
//IConnectIQService
package com.garmin.android.apps.connectmobile.connectiq;

import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQMessage;

interface IConnectIQService {
    boolean openStore(String applicationID);
    List<IQDevice> getConnectedDevices();
    List<IQDevice> getKnownDevices();

    // Remote device methods
    int getStatus(in IQDevice device);

    // Messages and Commands
    oneway void getApplicationInfo(String notificationPackage, String notificationAction, in IQDevice device, String applicationID);
    oneway void openApplication(String notificationPackage, String notificationAction, in IQDevice device, in IQApp app);

    // Pending intent will be fired to let the sdk know a message has been transferred.
    oneway void sendMessage(in IQMessage message, in IQDevice device, in IQApp app);
    oneway void sendImage(in IQMessage image, in IQDevice device, in IQApp app);

    // registers a companion app with the remote service so that it can receive messages from remote device.
    oneway void registerApp(in IQApp app, String notificationAction, String notificationPackage);
}