package app.aaps.shared.tests

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import org.mockito.ArgumentMatchers
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import java.io.Serializable

object BundleMock {

    fun mocked(map: HashMap<String?, Any?> = HashMap()): Bundle {
        val unsupported = Answer { throw UnsupportedOperationException() }
        val get = Answer { invocation: InvocationOnMock -> map[invocation.arguments[0]] }
        val getOrDefault = Answer { invocation: InvocationOnMock ->
            val key = invocation.arguments[0]
            if (map.containsKey(key)) map[key] else invocation.arguments[1]
        }
        val bundle: Bundle = mock()
        doAnswer { map.size }.whenever(bundle).size()
        doAnswer { map.isEmpty() }.whenever(bundle).isEmpty
        doAnswer { map.clear() }.whenever(bundle).clear()
        doAnswer { invocation: InvocationOnMock -> map.containsKey(invocation.arguments[0]) }.whenever(bundle).containsKey(ArgumentMatchers.anyString())
        doAnswer { invocation: InvocationOnMock -> map.remove(invocation.arguments[0]) }.whenever(bundle).remove(ArgumentMatchers.anyString())
        doAnswer { map.keys }.whenever(bundle).keySet()
        doAnswer { BundleMock::class.java.simpleName + "{map=" + map.toString() + "}" }.whenever(bundle).toString()
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putBoolean(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())
        whenever(bundle.getBoolean(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getBoolean(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putByte(ArgumentMatchers.anyString(), ArgumentMatchers.anyByte())
        whenever(bundle.getByte(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getByte(ArgumentMatchers.anyString(), ArgumentMatchers.anyByte())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putChar(ArgumentMatchers.anyString(), ArgumentMatchers.anyChar())
        whenever(bundle.getChar(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getChar(ArgumentMatchers.anyString(), ArgumentMatchers.anyChar())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyShort().toInt())
        whenever(bundle.getShort(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getShort(ArgumentMatchers.anyString(), ArgumentMatchers.anyShort())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putLong(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong())
        whenever(bundle.getLong(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getLong(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putFloat(ArgumentMatchers.anyString(), ArgumentMatchers.anyFloat())
        whenever(bundle.getFloat(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getFloat(ArgumentMatchers.anyString(), ArgumentMatchers.anyFloat())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putDouble(ArgumentMatchers.anyString(), ArgumentMatchers.anyDouble())
        whenever(bundle.getDouble(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getDouble(ArgumentMatchers.anyString(), ArgumentMatchers.anyDouble())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())
        whenever(bundle.getString(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putBooleanArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(BooleanArray::class.java))
        whenever(bundle.getBooleanArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putLongArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(LongArray::class.java))
        whenever(bundle.getLongArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putDoubleArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(DoubleArray::class.java))
        whenever(bundle.getDoubleArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putIntArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(IntArray::class.java))
        whenever(bundle.getIntArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
        whenever(bundle.getInt(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenAnswer(getOrDefault)
        doAnswer { unsupported }.whenever(bundle).putAll(ArgumentMatchers.any(Bundle::class.java))
        whenever(bundle.hasFileDescriptors()).thenAnswer(unsupported)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putShort(ArgumentMatchers.anyString(), ArgumentMatchers.anyShort())
        whenever(bundle.getShort(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getShort(ArgumentMatchers.anyString(), ArgumentMatchers.anyShort())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putFloat(ArgumentMatchers.anyString(), ArgumentMatchers.anyFloat())
        whenever(bundle.getFloat(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getFloat(ArgumentMatchers.anyString(), ArgumentMatchers.anyFloat())).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putCharSequence(ArgumentMatchers.anyString(), ArgumentMatchers.any(CharSequence::class.java))
        whenever(bundle.getCharSequence(ArgumentMatchers.anyString())).thenAnswer(get)
        whenever(bundle.getCharSequence(ArgumentMatchers.anyString(), ArgumentMatchers.any(CharSequence::class.java))).thenAnswer(getOrDefault)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putBundle(ArgumentMatchers.anyString(), ArgumentMatchers.any(Bundle::class.java))
        whenever(bundle.getBundle(ArgumentMatchers.anyString())).thenAnswer(get)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            whenever(bundle.getParcelable(ArgumentMatchers.anyString(), ArgumentMatchers.any<Class<*>>())).thenAnswer(get)
            whenever(bundle.getParcelableArray(ArgumentMatchers.anyString(), ArgumentMatchers.any<Class<*>>())).thenAnswer(get)
            whenever(bundle.getParcelableArrayList(ArgumentMatchers.anyString(), ArgumentMatchers.any<Class<*>>())).thenAnswer(get)
            whenever(bundle.getSparseParcelableArray(ArgumentMatchers.anyString(), ArgumentMatchers.any<Class<*>>())).thenAnswer(get)
            //whenever(bundle.getSerializable(ArgumentMatchers.anyString(), ArgumentMatchers.any<Class<*>>())).thenAnswer(get)
        } else {
            @Suppress("DEPRECATION")
            whenever<Any?>(bundle.getParcelable(ArgumentMatchers.anyString())).thenAnswer(get)
            @Suppress("DEPRECATION")
            whenever(bundle.getParcelableArray(ArgumentMatchers.anyString())).thenAnswer(get)
            //whenever(bundle.getParcelableArrayList(ArgumentMatchers.anyString())).thenAnswer(get)
            //whenever(bundle.getSparseParcelableArray(ArgumentMatchers.anyString())).thenAnswer(get)
            @Suppress("DEPRECATION")
            whenever(bundle.getSerializable(ArgumentMatchers.anyString())).thenAnswer(get)
        }
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putParcelable(ArgumentMatchers.anyString(), ArgumentMatchers.any(Parcelable::class.java))
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putParcelableArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(Array<Parcelable>::class.java))
        //doAnswer {  invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putParcelableArrayList(ArgumentMatchers.anyString(), ArgumentMatchers.any(ArrayList<out Parcelable>::class.java))
        //doAnswer {  invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putSparseParcelableArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(SparseArray<out Parcelable>::class.java))
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putSerializable(ArgumentMatchers.anyString(), ArgumentMatchers.any(Serializable::class.java))

        whenever(bundle.getIntegerArrayList(ArgumentMatchers.anyString())).thenAnswer(get)
        //doAnswer{ invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putIntegerArrayList(ArgumentMatchers.anyString(), ArgumentMatchers.any(ArrayList<Integer>::class.java))
        whenever(bundle.getStringArrayList(ArgumentMatchers.anyString())).thenAnswer(get)
        //doAnswer{ invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putStringArrayList(ArgumentMatchers.anyString(), ArgumentMatchers.any(ArrayList<String>::class.java))
        whenever(bundle.getCharSequenceArrayList(ArgumentMatchers.anyString())).thenAnswer(get)
        //doAnswer{ invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putCharSequenceArrayList(ArgumentMatchers.anyString(), ArgumentMatchers.any(ArrayList<CharSequence>::class.java))
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putCharArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(CharArray::class.java))
        whenever(bundle.getCharArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putByteArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(ByteArray::class.java))
        whenever(bundle.getByteArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putShortArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(ShortArray::class.java))
        whenever(bundle.getShortArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putFloatArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(FloatArray::class.java))
        whenever(bundle.getFloatArray(ArgumentMatchers.anyString())).thenAnswer(get)
        doAnswer { invocation: InvocationOnMock -> map[invocation.arguments[0] as String] = invocation.arguments[1] }.whenever(bundle).putCharSequenceArray(ArgumentMatchers.anyString(), ArgumentMatchers.any(Array<CharSequence>::class.java))
        whenever(bundle.getCharSequenceArray(ArgumentMatchers.anyString())).thenAnswer(get)
        return bundle
    }
}