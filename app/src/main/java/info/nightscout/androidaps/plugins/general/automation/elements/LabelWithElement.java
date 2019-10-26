package info.nightscout.androidaps.plugins.general.automation.elements;

import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;

public class LabelWithElement extends Element {
    final Element element;
    final String textPre;
    final String textPost;

    public LabelWithElement(String textPre, String textPost, Element element) {
        this.element = element;
        this.textPre = textPre;
        this.textPost = textPost;
    }

    @Override
    public void addToLayout(LinearLayout root) {
        // container layout
        LinearLayout layout = new LinearLayout(root.getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // text view pre element
        int px = MainApp.dpToPx(10);
        TextView textViewPre = new TextView(root.getContext());
        textViewPre.setText(textPre);
        textViewPre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        //textViewPre.setWidth(MainApp.dpToPx(120));
        textViewPre.setPadding(px, px, px, px);
        textViewPre.setTypeface(textViewPre.getTypeface(), Typeface.BOLD);
        layout.addView(textViewPre);

        TextView spacer = new TextView(root.getContext());
        spacer.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        layout.addView(spacer);

        // add element to layout
        element.addToLayout(layout);

        // text view post element
        if (textPost != null) {
            px = MainApp.dpToPx(5);
            TextView textViewPost = new TextView(root.getContext());
            textViewPost.setText(textPost);
            textViewPost.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            //textViewPost.setWidth(MainApp.dpToPx(45));
            textViewPost.setPadding(px, px, px, px);
            textViewPost.setTypeface(textViewPost.getTypeface(), Typeface.BOLD);
            layout.addView(textViewPost);
        }

        // add layout to root layout
        root.addView(layout);
    }
}
