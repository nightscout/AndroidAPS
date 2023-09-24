package info.nightscout.androidaps.comm

import android.os.Build
import android.util.Log
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import javax.inject.Inject

class ExceptionHandlerWear @Inject constructor(
    private val rxBus: RxBus,
) {

    private var mDefaultUEH: Thread.UncaughtExceptionHandler? = null

    private val mWearUEH = Thread.UncaughtExceptionHandler { thread, ex ->
        Log.d("WEAR", "uncaughtException :" + ex.message)

        // Pass the exception to the bus which will send the data upstream to your Smartphone/Tablet
        val wearException = EventData.WearException(
            timeStamp = System.currentTimeMillis(),
            exception = exceptionToByteArray(ex),
            board = Build.BOARD,
            sdk = Build.VERSION.SDK_INT.toString(),
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            product = Build.PRODUCT
        )
        rxBus.send(EventWearToMobile(wearException))

        // Let the default UncaughtExceptionHandler take it from here
        mDefaultUEH?.uncaughtException(thread, ex)
    }

    fun register() {
        mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mWearUEH)
    }

    private fun exceptionToByteArray(ex: Throwable): ByteArray {
        ex.stackTrace // Make sure the stacktrace gets built up
        val bos = ByteArrayOutputStream()
        var oos: ObjectOutputStream? = null
        try {
            oos = ObjectOutputStream(bos)
            oos.writeObject(ex)
            return bos.toByteArray()

        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                oos?.close()
            } catch (exx: IOException) {
                // Ignore close exception
            }
            try {
                bos.close()
            } catch (exx: IOException) {
                // Ignore close exception
            }
        }
        return byteArrayOf()
    }

}
