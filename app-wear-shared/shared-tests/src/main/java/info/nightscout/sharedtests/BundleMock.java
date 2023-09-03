package info.nightscout.sharedtests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

@SuppressWarnings({"unused", "rawtypes", "SuspiciousMethodCalls", "unchecked", "deprecation"})
public final class BundleMock {

    public static Bundle mock() {
        return mock(new HashMap<>());
    }

    public static Bundle mock(final HashMap<String, Object> map) {

        Answer unsupported = invocation -> {
            throw new UnsupportedOperationException();
        };
        Answer put = invocation -> {
            map.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
        Answer<Object> get = invocation -> map.get(invocation.getArguments()[0]);
        Answer<Object> getOrDefault = invocation -> {
            Object key = invocation.getArguments()[0];
            return map.containsKey(key) ? map.get(key) : invocation.getArguments()[1];
        };

        Bundle bundle = Mockito.mock(Bundle.class);

        doAnswer(invocation -> map.size()).when(bundle).size();
        doAnswer(invocation -> map.isEmpty()).when(bundle).isEmpty();
        doAnswer(invocation -> {
            map.clear();
            return null;
        }).when(bundle).clear();
        doAnswer(invocation -> map.containsKey(invocation.getArguments()[0])).when(bundle).containsKey(anyString());
        doAnswer(invocation -> map.get(invocation.getArguments()[0])).when(bundle).get(anyString());
        doAnswer(invocation -> {
            map.remove(invocation.getArguments()[0]);
            return null;
        }).when(bundle).remove(anyString());
        doAnswer(invocation -> map.keySet()).when(bundle).keySet();
        doAnswer(invocation -> BundleMock.class.getSimpleName() + "{map=" + map.toString() + "}").when(bundle).toString();

        doAnswer(put).when(bundle).putBoolean(anyString(), anyBoolean());
        when(bundle.getBoolean(anyString())).thenAnswer(get);
        when(bundle.getBoolean(anyString(), anyBoolean())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putByte(anyString(), anyByte());
        when(bundle.getByte(anyString())).thenAnswer(get);
        when(bundle.getByte(anyString(), anyByte())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putChar(anyString(), anyChar());
        when(bundle.getChar(anyString())).thenAnswer(get);
        when(bundle.getChar(anyString(), anyChar())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putInt(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putLong(anyString(), anyLong());
        when(bundle.getLong(anyString())).thenAnswer(get);
        when(bundle.getLong(anyString(), anyLong())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putDouble(anyString(), anyDouble());
        when(bundle.getDouble(anyString())).thenAnswer(get);
        when(bundle.getDouble(anyString(), anyDouble())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putString(anyString(), anyString());
        when(bundle.getString(anyString())).thenAnswer(get);
        when(bundle.getString(anyString(), anyString())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBooleanArray(anyString(), any(boolean[].class));
        when(bundle.getBooleanArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putLongArray(anyString(), any(long[].class));
        when(bundle.getLongArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putDoubleArray(anyString(), any(double[].class));
        when(bundle.getDoubleArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntArray(anyString(), any(int[].class));
        when(bundle.getIntArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putInt(anyString(), anyInt());
        when(bundle.getInt(anyString())).thenAnswer(get);
        when(bundle.getInt(anyString(), anyInt())).thenAnswer(getOrDefault);

        doAnswer(unsupported).when(bundle).putAll(any(Bundle.class));
        when(bundle.hasFileDescriptors()).thenAnswer(unsupported);

        doAnswer(put).when(bundle).putShort(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putCharSequence(anyString(), any(CharSequence.class));
        when(bundle.getCharSequence(anyString())).thenAnswer(get);
        when(bundle.getCharSequence(anyString(), any(CharSequence.class))).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBundle(anyString(), any(Bundle.class));
        when(bundle.getBundle(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelable(anyString(), any(Parcelable.class));
        when(bundle.getParcelable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArray(anyString(), any(Parcelable[].class));
        when(bundle.getParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArrayList(anyString(), any(ArrayList.class));
        when(bundle.getParcelableArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSparseParcelableArray(anyString(), any(SparseArray.class));
        when(bundle.getSparseParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSerializable(anyString(), any(Serializable.class));
        when(bundle.getSerializable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntegerArrayList(anyString(), any(ArrayList.class));
        when(bundle.getIntegerArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putStringArrayList(anyString(), any(ArrayList.class));
        when(bundle.getStringArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArrayList(anyString(), any(ArrayList.class));
        when(bundle.getCharSequenceArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharArray(anyString(), any(char[].class));
        when(bundle.getCharArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putByteArray(anyString(), any(byte[].class));
        when(bundle.getByteArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putShortArray(anyString(), any(short[].class));
        when(bundle.getShortArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putFloatArray(anyString(), any(float[].class));
        when(bundle.getFloatArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArray(anyString(), any(CharSequence[].class));
        when(bundle.getCharSequenceArray(anyString())).thenAnswer(get);

        return bundle;
    }
}