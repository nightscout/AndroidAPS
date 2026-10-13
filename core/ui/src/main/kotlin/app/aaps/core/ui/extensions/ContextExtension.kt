package app.aaps.core.ui.extensions

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity

fun Context?.scanForActivity(): AppCompatActivity? =
    when (this) {
        null                 -> null
        is AppCompatActivity -> this
        is ContextWrapper    -> this.baseContext.scanForActivity()
        else                 -> null
    }
