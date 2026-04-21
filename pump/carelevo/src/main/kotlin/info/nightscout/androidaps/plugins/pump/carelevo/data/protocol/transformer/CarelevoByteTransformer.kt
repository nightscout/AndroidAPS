package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

internal interface CarelevoByteTransformer<T, R> {
    fun transform(item : T) : R
}