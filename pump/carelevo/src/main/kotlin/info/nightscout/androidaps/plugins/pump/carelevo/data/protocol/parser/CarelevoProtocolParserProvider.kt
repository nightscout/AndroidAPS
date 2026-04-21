package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser

class CarelevoProtocolParserProvider {

    private val parsers = mutableMapOf<Class<*>, CarelevoProtocolParser<ByteArray, *>>()
    private val models = mutableMapOf<Int, Class<*>>()

    internal fun <T> registerParser(modelType : Class<T>, parser : CarelevoProtocolParser<ByteArray, T>) {
        parsers[modelType] = parser
        models[parser.command] = modelType
    }

    internal fun <T> getParser(modelType : Class<T>) : CarelevoProtocolParser<ByteArray, T>? {
        return parsers[modelType] as? CarelevoProtocolParser<ByteArray, T>
    }

    internal fun getModel(command : Int) : Class<*>? {
        return models[command]
    }

    internal fun <T> parseByteArray(modelType : Class<T>, byteArray : ByteArray) : T? {
        val parser = parsers[modelType] as? CarelevoProtocolParser<ByteArray, T>
        return parser?.parse(byteArray)
    }
}