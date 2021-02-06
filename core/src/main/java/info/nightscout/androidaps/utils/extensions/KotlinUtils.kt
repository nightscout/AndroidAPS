package info.nightscout.androidaps.utils.extensions

object Do { inline infix fun <reified T> exhaustive(any: T?): T? = any }
