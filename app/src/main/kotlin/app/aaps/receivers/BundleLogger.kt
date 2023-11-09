package app.aaps.receivers

import android.os.Bundle

object BundleLogger {

    fun log(bundle: Bundle?): String {
        if (bundle == null) {
            return "null"
        }
        var string = "Bundle{"
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            string += " " + key + " => " + bundle[key] + ";"
        }
        string += " }Bundle"
        return string
    }
}