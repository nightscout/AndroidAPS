package info.nightscout.androidaps.comm

import android.content.Context
import android.util.Log

class ExceptionWear(private val context: Context) {

    private var mDefaultUEH: Thread.UncaughtExceptionHandler? = null

    private val mWearUEH = Thread.UncaughtExceptionHandler { thread, ex ->
        Log.d("WEAR", "uncaughtException :" + ex.message)
        // Pass the exception to a Service which will send the data upstream to your Smartphone/Tablet
        ExceptionService.reportException(context, ex)
        // Let the default UncaughtExceptionHandler take it from here
        mDefaultUEH?.uncaughtException(thread, ex)
    }

    init {
        mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mWearUEH)
    }

}
