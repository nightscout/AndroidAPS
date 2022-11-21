package info.nightscout.core.utils.extensions

import android.os.Bundle
import androidx.work.Data

fun Data.Builder.copyString(key: String, bundle:  Bundle?, defaultValue : String? = ""): Data.Builder =
    this.also { putString(key, bundle?.getString(key) ?: defaultValue) }

fun Data.Builder.copyLong(key: String, bundle:  Bundle?, defaultValue : Long = 0): Data.Builder =
    this.also { putLong(key, bundle?.getLong(key) ?: defaultValue) }

fun Data.Builder.copyInt(key: String, bundle:  Bundle?, defaultValue : Int = 0): Data.Builder =
    this.also { putInt(key, bundle?.getInt(key) ?: defaultValue) }

fun Data.Builder.copyDouble(key: String, bundle:  Bundle?, defaultValue : Double = 0.0): Data.Builder =
    this.also { putDouble(key, bundle?.getDouble(key) ?: defaultValue) }
