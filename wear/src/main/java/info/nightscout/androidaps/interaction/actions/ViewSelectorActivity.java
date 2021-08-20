package info.nightscout.androidaps.interaction.actions;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 13/02/17.
 */
public class ViewSelectorActivity extends Activity {


    View getInflatedPlusMinusView(ViewGroup container) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        int design = Integer.parseInt(sharedPrefs.getString("input_design", "1"));

        if (design == 2){
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_quickrighty, container, false);
        } else if (design == 3) {
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_quicklefty, container, false);
        } else if (design == 4) {
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_viktoria, container, false);
        }

        //default
        return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item, container, false);
    }

    void setLabelToPlusMinusView(View view, String labelText){
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        int design = Integer.parseInt(sharedPrefs.getString("input_design", "1"));

        if (design == 4){
            //@LadyViktoria: Here the label can be set differently, if you like.
            final TextView textView = view.findViewById(R.id.label);
            textView.setText(labelText);
        } else {
            final TextView textView = view.findViewById(R.id.label);
            textView.setText(labelText);
        }
    }

}
