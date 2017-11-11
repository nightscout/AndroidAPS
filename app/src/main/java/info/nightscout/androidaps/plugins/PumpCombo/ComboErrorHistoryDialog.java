package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;

import de.jotomo.ruffy.spi.history.PumpError;
import info.nightscout.androidaps.R;

/**
 * Created by adrian on 17/08/17.
 */

public class ComboErrorHistoryDialog extends DialogFragment {
    private static Logger log = LoggerFactory.getLogger(ComboErrorHistoryDialog.class);

    private TextView text;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.combo_error_history_fragment, container, false);
        text = (TextView) layout.findViewById(R.id.combo_error_history_text);
        List<PumpError> errors = ComboPlugin.getPlugin().getPump().history.pumpErrorHistory;
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM HH:mm");
        if (errors.isEmpty()) {
            text.setText("No errors. To retrieve the error history from the pump, long press the Refresh button.");
        } else {
            for (PumpError error : errors) {
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
                sb.append("\n");
            }
            text.setText(sb.toString());
        }
        return layout;
    }
}
