package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.List;

import de.jotomo.ruffy.spi.history.PumpAlert;
import info.nightscout.androidaps.R;

public class ComboAlertHistoryDialog extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.combo_alert_history_fragment, container, false);
        TextView text = (TextView) layout.findViewById(R.id.combo_error_history_text);
        List<PumpAlert> errors = ComboPlugin.getPlugin().getPump().errorHistory;
        StringBuilder sb = new StringBuilder();
        // TODO i18n
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM. HH:mm");
        if (errors.isEmpty()) {
            text.setText("To retrieve the alert history from the pump, long press the Refresh button.");
        } else {
            boolean first = true;
            for (PumpAlert error : errors) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\n");
                }
                sb.append(simpleDateFormat.format(error.timestamp));
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
