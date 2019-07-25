package info.nightscout.androidaps.plugins.general.automation.elements;

import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class StaticLabel extends Element {
    String label;

    public StaticLabel(String label) {
        super();
        this.label = label;
    }

    public StaticLabel(int resourceId) {
        super();
        this.label = MainApp.gs(resourceId);
    }

    @Override
    public void addToLayout(LinearLayout root) {
        // text view pre element
        int px = MainApp.dpToPx(10);
        TextView textView = new TextView(root.getContext());
        textView.setText(label);
//       textViewPre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(px, px, px, px);
        textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        textView.setBackgroundColor(MainApp.gc(R.color.mdtp_line_dark));
        // add element to layout
        root.addView(textView);
    }
}
