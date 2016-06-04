package info.nightscout.androidaps.plugins.Test;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.R;

/**
 * Created by mike on 30.05.2016.
 */
public class TestFragment extends Fragment {
    private static TextView textView;
    private static TestFragment instance;

    public TestFragment() {
        super();
    }


    public static TestFragment newInstance() {
        if (instance == null)
            instance = new TestFragment();
        return instance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstance) {
        View layout = inflater.inflate(R.layout.app_fragment, container, false);
        textView = (TextView) layout.findViewById(R.id.position);
        Bundle bundle = getArguments();
        if (bundle != null) {
            textView.setText("This is page of tab " + bundle.getString("name"));
        }
        return layout;
    }
}
