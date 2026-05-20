package app.aaps.pump.carelevo.data.protocol.transformer

internal interface CarelevoByteTransformer<T, R> {

    fun transform(item: T): R
}