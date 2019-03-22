package info.nightscout.androidaps.plugins.pump.insight.utils;

import java.util.HashMap;
import java.util.Map;

public class IDStorage<T, I> {

    private Map<T, I> types = new HashMap<>();
    private Map<I, T> ids = new HashMap<>();

    public void put(T type, I id) {
        types.put(type, id);
        ids.put(id, type);
    }

    public T getType(I type) {
        return ids.get(type);
    }

    public I getID(T id) {
        return types.get(id);
    }

}
