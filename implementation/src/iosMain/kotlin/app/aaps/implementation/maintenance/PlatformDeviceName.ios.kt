package app.aaps.implementation.maintenance

import platform.UIKit.UIDevice

/** The name the user gave the phone in Settings, which is what they will recognise. */
actual fun platformDeviceName(): String = UIDevice.currentDevice.name
