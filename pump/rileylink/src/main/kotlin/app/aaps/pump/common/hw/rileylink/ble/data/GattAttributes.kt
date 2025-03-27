package app.aaps.pump.common.hw.rileylink.ble.data

import java.util.HashMap
import java.util.UUID

/**
 * Created by geoff on 5/21/16.
 */
@Suppress("SpellCheckingInspection")
object GattAttributes {

    // NOTE: these uuid strings must be lower case!
    var PREFIX: String = "0000"
    var SUFFIX: String = "-0000-1000-8000-00805f9b34fb"

    // Generic Access
    var SERVICE_GA: String = PREFIX + "1800" + SUFFIX
    var CHARA_GA_NAME: String = PREFIX + "2a00" + SUFFIX // RileyLink RFSpy
    var CHARA_GA_APPEARANCE: String = PREFIX + "2a01" + SUFFIX // 0000
    var CHARA_GA_PPCP: String = PREFIX + "2a04" + SUFFIX // 0000
    var CHARA_GA_CAR: String = PREFIX + "2aa6" + SUFFIX // 0000

    // Generic Attribute
    var SERVICE_G_ATTR: String = PREFIX + "1801" + SUFFIX

    // Battery Service
    var SERVICE_BATTERY: String = PREFIX + "180f" + SUFFIX // Battery
    var CHARA_BATTERY_LEVEL: String = PREFIX + "2a19" + SUFFIX

    // RileyLink Radio Service
    var SERVICE_RADIO: String = "0235733b-99c5-4197-b856-69219c2a3845"
    var CHARA_RADIO_DATA: String = "c842e849-5028-42e2-867c-016adada9155"
    var CHARA_RADIO_RESPONSE_COUNT: String = "6e6c7910-b89e-43a5-a0fe-50c5e2b81f4a"
    var CHARA_RADIO_TIMER_TICK: String = "6e6c7910-b89e-43a5-78af-50c5e2b86f7e"
    var CHARA_RADIO_CUSTOM_NAME: String = "d93b2af0-1e28-11e4-8c21-0800200c9a66"
    var CHARA_RADIO_VERSION: String = "30d99dc9-7c91-4295-a051-0a104d238cf2"
    var CHARA_RADIO_LED_MODE: String = "c6d84241-f1a7-4f9c-a25f-fce16732f14e"

    // Secure DFU Service (Orange 1.5 - 3.2)
    var SERVICE_DFU: String = "0000fe59-0000-1000-8000-00805f9b34fb"
    var CHARA_BUTTONLESS_DFU: String = "8ec90003-f315-4f60-9fb8-838830daea50"

    // Nordic UART Service (Orange 2.1 - 3.2)
    var SERVICE_NORDIC_UART: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    var CHARA_NORDIC_RX: String = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    var CHARA_NORDIC_TX: String = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    // Orange Radio Service
    var SERVICE_RADIO_ORANGE: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    var CHARA_NOTIFICATION_ORANGE: String = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    private val attributes = HashMap<String?, String?>()
    private val attributesRileyLinkSpecific: MutableMap<String?, String?>

    // table of names for uuids
    init {

        attributes.put(SERVICE_GA, "Generic Access")
        attributes.put(CHARA_GA_NAME, "Device Name") //
        attributes.put(CHARA_GA_APPEARANCE, "Appearance") //
        attributes.put(CHARA_GA_PPCP, "Peripheral Preferred Connection Parameters")
        attributes.put(CHARA_GA_CAR, "Central Address Resolution")

        attributes.put(SERVICE_G_ATTR, "Generic Attribute")

        attributes.put(SERVICE_BATTERY, "Battery Service")
        attributes.put(CHARA_BATTERY_LEVEL, "Battery Level")

        attributes.put(SERVICE_RADIO, "Radio Interface Service")
        attributes.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name")
        attributes.put(CHARA_RADIO_DATA, "Data")
        attributes.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count")
        attributes.put(CHARA_RADIO_TIMER_TICK, "Timer Tick")
        attributes.put(CHARA_RADIO_VERSION, "Version") // firmwareVersion
        attributes.put(CHARA_RADIO_LED_MODE, "Led Mode")

        attributes.put(SERVICE_DFU, "Secure DFU Service")
        attributes.put(CHARA_BUTTONLESS_DFU, "Buttonless DFU")

        attributes.put(SERVICE_NORDIC_UART, "Nordic UART Service")
        attributes.put(CHARA_NORDIC_RX, "RX Characteristic")
        attributes.put(CHARA_NORDIC_TX, "TX Characteristic")

        attributesRileyLinkSpecific = HashMap<String?, String?>()

        attributesRileyLinkSpecific.put(SERVICE_RADIO, "Radio Interface") // a
        attributesRileyLinkSpecific.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name")
        attributesRileyLinkSpecific.put(CHARA_RADIO_DATA, "Data")
        attributesRileyLinkSpecific.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count")
        attributesRileyLinkSpecific.put(CHARA_RADIO_TIMER_TICK, "Timer Tick")
        attributesRileyLinkSpecific.put(CHARA_RADIO_VERSION, "Version") // firmwareVersion
        attributesRileyLinkSpecific.put(CHARA_RADIO_LED_MODE, "Led Mode")

        attributesRileyLinkSpecific.put(SERVICE_RADIO_ORANGE, "Orange Radio Interface")
        attributesRileyLinkSpecific.put(CHARA_NOTIFICATION_ORANGE, "Orange Notification")
    }

    fun lookup(uuid: UUID): String? {
        return lookup(uuid.toString())
    }

    fun lookup(uuid: String?, defaultName: String? = uuid): String? {
        val name = attributes[uuid]
        return if (name == null) defaultName else name
    }

    // we check for specific UUID (Radio ones, because those seem to be unique
    fun isRileyLink(uuid: UUID): Boolean {
        return attributesRileyLinkSpecific.containsKey(uuid.toString())
    }

    fun isOrange(uuid: UUID): Boolean {
        return SERVICE_RADIO_ORANGE == uuid.toString()
    }
}
