package info.nightscout.androidaps.interfaces.constrains;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 19.03.2018.
 */

public class Constraint {
    List<String> reasons = new ArrayList<>();

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
