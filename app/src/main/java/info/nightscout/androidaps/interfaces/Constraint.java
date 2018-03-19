package info.nightscout.androidaps.interfaces;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 19.03.2018.
 */

public class Constraint<T> {
    T value;
    List<String> reasons = new ArrayList<>();

    public Constraint(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public Constraint<T> set(T value) {
        this.value = value;
        return this;
    }

    public Constraint<T> set(T value, String reason) {
        this.value = value;
        reason(reason);
        return this;
    }

    public Constraint reason(String reason) {
        reasons.add(reason);
        return this;
    }

    public String getReasons() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r: reasons) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        return sb.toString();
    }
}
