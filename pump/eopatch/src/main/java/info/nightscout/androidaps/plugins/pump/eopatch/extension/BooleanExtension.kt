package info.nightscout.androidaps.plugins.pump.eopatch.extension

fun <T> Boolean.takeOne(whenTrue: T, whenFalse: T): T {
    return if(this) whenTrue else whenFalse
}