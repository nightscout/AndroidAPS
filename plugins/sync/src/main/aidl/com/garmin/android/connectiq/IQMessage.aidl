/**
 * Copyright (C) 2014 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.connectiq;

parcelable IQMessage {
  const int SUCCESS = 0;
  const int FAILURE_UNKNOWN = 1;
  const int FAILURE_INVALID_FORMAT = 2;
  const int FAILURE_MESSAGE_TOO_LARGE = 3;
  const int FAILURE_UNSUPPORTED_TYPE = 4;
  const int FAILURE_DURING_TRANSFER = 5;
  const int FAILURE_INVALID_DEVICE = 6;
  const int FAILURE_DEVICE_NOT_CONNECTED = 7;

  byte[] messageData;
  String notificationPackage;
  String notificationAction;
}