package info.nightscout.comboctl.base

object Constants {
    /**
     * Hard-coded Bluetooth pairing PIN used by the Combo.
     *
     * This is not to be confused with the PIN that is displayed on
     * the  Combo's LCD when pairing it with a device. That other PIN
     * is part of a non-standard, Combo specific pairing process that
     * happens _after_ the Bluetooth pairing has been completed.
     */
    const val BT_PAIRING_PIN = "}gZ='GD?gj2r|B}>"

    /**
     * SDP service record name the Combo searches for during discovery.
     *
     * Any SerialPort SDP service record that is not named like
     * this is ignored by the Combo.
     */
    const val BT_SDP_SERVICE_NAME = "SerialLink"

    /**
     * Client software version number for TL_REQUEST_ID packets.
     *
     * This version number is used as the payload of TL_REQUEST_ID
     * packets that are sent to the Combo during the pairing process.
     */
    const val CLIENT_SOFTWARE_VERSION = 10504

    /**
     * Serial number for AL_CTRL_CONNECT packets.
     *
     * This serial number is used as the payload of AL_CTRL_CONNECT
     * packets that are sent to the Combo when connecting to it.
     */
    const val APPLICATION_LAYER_CONNECT_SERIAL_NUMBER = 12345
}
