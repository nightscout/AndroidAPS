package app.aaps.core.validators.extensions

import android.content.Context
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.PreferenceKey

fun PreferenceKey.stringKey(rh: ResourceHelper) = rh.gs(key)
fun PreferenceKey.stringKey(context: Context) = context.getString(key)