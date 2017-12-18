package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffy.spi.history.Tdd;
import info.nightscout.androidaps.R;

public class ComboTddHistoryDialog extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.combo_tdd_history_fragment, container, false);
        TextView text = (TextView) layout.findViewById(R.id.combo_tdd_history_text);
        List<Tdd> tdds = ComboPlugin.getPlugin().getPump().tddHistory;
        if (tdds.isEmpty()) {
            text.setText(R.string.combo_no_tdd_data_note);
        } else {
            StringBuilder sb = new StringBuilder();
            DateFormat dateFormatter = DateFormat.getDateInstance();
            double avg = 0;
            double min = 999;
            double max = 0;
            int count = 0;
            for (Tdd tdd : tdds) {
                if (tdd.total > 0) {
                    avg += tdd.total;
                    count++;
                }
                if (tdd.total < min) min = tdd.total;
                if (tdd.total > max) max = tdd.total;
            }
            avg = avg / count;
            sb.append(String.format(Locale.getDefault(), getString(R.string.combo_tdd_minimum), min));
            sb.append("\n");
            sb.append(String.format(Locale.getDefault(), getString(R.string.combo_tdd_average), avg));
            sb.append("\n");
            sb.append(String.format(Locale.getDefault(), getString(R.string.combo_tdd_maximum), max));
            sb.append("\n");
            for (Tdd tdd : tdds) {
                sb.append("\n");
                sb.append(dateFormatter.format(tdd.timestamp));
                sb.append("  ");
                sb.append(String.format(Locale.getDefault(), "%3.1f", tdd.total));
                sb.append(" U");
            }
            text.setText(sb.toString());
        }
        return layout;
    }
}
