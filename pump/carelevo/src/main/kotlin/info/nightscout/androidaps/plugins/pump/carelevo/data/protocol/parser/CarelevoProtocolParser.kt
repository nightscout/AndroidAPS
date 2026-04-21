package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser

internal interface CarelevoProtocolParser<T, out R> {
    val command : Int
    fun parse(data : T) : R
}