package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.List;

import de.jotomo.ruffy.spi.history.PumpAlert;
import info.nightscout.androidaps.R;

public class ComboAlertHistoryDialog extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.combo_alert_history_fragment, container, false);
        TextView text = (TextView) layout.findViewById(R.id.combo_error_history_text);
        List<PumpAlert> errors = ComboPlugin.getPlugin().getPump().errorHistory;
        if (errors.isEmpty()) {
            text.setText(R.string.combo_no_alert_data_note);
        } else {
            StringBuilder sb = new StringBuilder();
            DateFormat dateTimeFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            boolean first = true;
            for (PumpAlert error : errors) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\n");
                }
                sb.append(dateTimeFormatter.format(error.timestamp));
                sb.append("  ");
                sb.append(error.message);
                if (error.warningCode != null) {
                    sb.append(" (W");
                    sb.append(error.warningCode);
                    sb.append(")");
                }
                if (error.errorCode != null) {
                    sb.append(" (E");
                    sb.append(error.errorCode);
                    sb.append(")");
                }
            }
            text.setText(sb.toString());
        }
        return layout;
    }
}
