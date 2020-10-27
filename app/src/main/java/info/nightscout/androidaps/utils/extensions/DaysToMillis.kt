package info.nightscout.androidaps.utils.extensions

import java.util.concurrent.TimeUnit

fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)
