package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.List;

import de.jotomo.ruffy.spi.history.Tdd;
import info.nightscout.androidaps.R;

public class ComboTddHistoryDialog extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.combo_tdd_history_fragment, container, false);
        TextView text = (TextView) layout.findViewById(R.id.combo_tdd_history_text);
        List<Tdd> tdds = ComboPlugin.getPlugin().getPump().tddHistory;
        StringBuilder sb = new StringBuilder();
        DateFormat dateFormatter = DateFormat.getDateInstance();
        if (tdds.isEmpty()) {
            text.setText(R.string.combo_empty_tdd_history_note);
        } else {
            boolean first = true;
            for (Tdd tdd : tdds) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\n");
                }
                sb.append(dateFormatter.format(tdd.timestamp));
                sb.append("  ");
                // TODO i18n
                sb.append(String.format("%.1f", tdd.total));
                sb.append(" U");
            }
            text.setText(sb.toString());
        }
        return layout;
    }
}
