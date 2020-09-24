package info.nightscout.androidaps.testing.mocks;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SharedPreferencesMock implements SharedPreferences {

    private final EditorInternals editor = new EditorInternals();

    class EditorInternals implements Editor {

        Map<String, Object> innerMap = new HashMap<>();

        @Override
        public Editor putString(String k, @Nullable String v) {
            innerMap.put(k, v);
            return this;
        }

        @Override
        public Editor putStringSet(String k, @Nullable Set<String> set) {
            innerMap.put(k, set);
            return this;
        }

        @Override
        public Editor putInt(String k, int i) {
            innerMap.put(k, i);
            return this;
        }

        @Override
        public Editor putLong(String k, long l) {
            innerMap.put(k, l);
            return this;
        }

        @Override
        public Editor putFloat(String k, float v) {
            innerMap.put(k, v);
            return this;
        }

        @Override
        public Editor putBoolean(String k, boolean b) {
            innerMap.put(k, b);
            return this;
        }

        @Override
        public Editor remove(String k) {
            innerMap.remove(k);
            return this;
        }

        @Override
        public Editor clear() {
            innerMap.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public void apply() {

        }
    }

    @Override
    public Map<String, ?> getAll() {
        return editor.innerMap;
    }

    @Nullable
    @Override
    public String getString(String k, @Nullable String s) {
        if (editor.innerMap.containsKey(k)) {
            return (String) editor.innerMap.get(k);
        } else {
            return s;
        }
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String k, @Nullable Set<String> set) {
        if (editor.innerMap.containsKey(k)) {
            return (Set<String>) editor.innerMap.get(k);
        } else {
            return set;
        }
    }

    @Override
    public int getInt(String k, int i) {
        if (editor.innerMap.containsKey(k)) {
            return (Integer) editor.innerMap.get(k);
        } else {
            return i;
        }
    }

    @Override
    public long getLong(String k, long l) {
        if (editor.innerMap.containsKey(k)) {
            return (Long) editor.innerMap.get(k);
        } else {
            return l;
        }
    }

    @Override
    public float getFloat(String k, float v) {
        if (editor.innerMap.containsKey(k)) {
            return (Float) editor.innerMap.get(k);
        } else {
            return v;
        }
    }

    @Override
    public boolean getBoolean(String k, boolean b) {
        if (editor.innerMap.containsKey(k)) {
            return (Boolean) editor.innerMap.get(k);
        } else {
            return b;
        }
    }

    @Override
    public boolean contains(String k) {
        return editor.innerMap.containsKey(k);
    }

    @Override
    public Editor edit() {
        return editor;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {

    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {

    }
}
