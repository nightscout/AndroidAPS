package info.nightscout.androidaps.plugins.general.automation.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class EditEventDialog extends DialogFragment {
    public static EditEventDialog newInstance() {

        Bundle args = new Bundle();

        EditEventDialog fragment = new EditEventDialog();
        fragment.setArguments(args);

        return fragment;
    }
}
