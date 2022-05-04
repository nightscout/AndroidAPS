package info.nightscout.androidaps.testing.mockers

import android.os.Bundle
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import info.nightscout.androidaps.interaction.utils.Constants
import info.nightscout.androidaps.interaction.utils.WearUtil
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class WearUtilMocker(private val wearUtil: WearUtil) {

    private var clockMsDiff = 0L
    fun prepareMock() {
        resetClock()

        // because we cleverly used timestamp() by implementation, we can mock it
        // and control the time in tests
        Mockito.`when`(wearUtil.timestamp()).thenReturn(REF_NOW + clockMsDiff)
    }

    fun prepareMockNoReal() {
        resetClock()
        Mockito.doAnswer { invocation: InvocationOnMock? -> REF_NOW + clockMsDiff }.`when`(wearUtil).timestamp()
        Mockito.doReturn(null).`when`(wearUtil).getWakeLock(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
    }

    fun resetClock() {
        clockMsDiff = 0L
    }

    fun progressClock(byMilliseconds: Long) {
        clockMsDiff += byMilliseconds
    }

    fun setClock(atMillisecondsSinceEpoch: Long) {
        clockMsDiff = atMillisecondsSinceEpoch - REF_NOW
    }

    fun backInTime(d: Int, h: Int, m: Int, s: Int): Long {
        return REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s)
    }

    @Suppress("UNCHECKED_CAST")
    private val bundleToDataMapMock: Answer<*> = Answer { invocation: InvocationOnMock ->
        val map = DataMap()
        val bundle = invocation.getArgument<Bundle>(0)
        for (key in bundle.keySet()) {
            val v = bundle[key]
            if (v is Asset) map.putAsset(key, v)
            if (v is Boolean) map.putBoolean(key, v)
            if (v is Byte) map.putByte(key, (v as Byte?)!!)
            if (v is ByteArray) map.putByteArray(key, v)
            if (v is DataMap) map.putDataMap(key, v)
            if (v is Double) map.putDouble(key, v)
            if (v is Float) map.putFloat(key, (v as Float))
            if (v is FloatArray) map.putFloatArray(key, v)
            if (v is Int) map.putInt(key, v)
            if (v is Long) map.putLong(key, v)
            if (v is LongArray) map.putLongArray(key, v)
            if (v is String) map.putString(key, v)
            if (v is Array<*>) map.putStringArray(key, v as Array<String>)
            if (v is ArrayList<*>) {
                if (v.isNotEmpty()) {
                    if (v[0] is Int) {
                        map.putIntegerArrayList(key, v as ArrayList<Int>)
                    }
                    if (v[0] is String) {
                        map.putStringArrayList(key, v as ArrayList<String>)
                    }
                    if (v[0] is DataMap) {
                        map.putDataMapArrayList(key, v as ArrayList<DataMap>)
                    }
                }
            }
        }
        map
    }

    companion object {

        const val REF_NOW = 1572610530000L
    }
}