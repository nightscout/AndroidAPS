package info.nightscout.androidaps.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;

public class AgreementActivity extends NoSplashActivity {
    boolean IUnderstand;
    CheckBox agreeCheckBox;
    Button saveButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agreement);
        IUnderstand = SP.getBoolean(R.string.key_i_understand, false);
        setContentView(R.layout.activity_agreement);
        agreeCheckBox = (CheckBox)findViewById(R.id.agreementCheckBox);
        agreeCheckBox.setChecked(IUnderstand);
        saveButton = (Button)findViewById(R.id.agreementSaveButton);
        addListenerOnButton();
    }

    public void addListenerOnButton() {
        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                SP.putBoolean(R.string.key_i_understand, agreeCheckBox.isChecked());

                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                finish();
            }

        });
    }
}
