package com.gxwtech.roundtrip2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.gxwtech.roundtrip2.CommunicationService.Objects.Bolus;
import com.gxwtech.roundtrip2.CommunicationService.Objects.Integration;
import com.gxwtech.roundtrip2.CommunicationService.Objects.RealmManager;
import com.gxwtech.roundtrip2.CommunicationService.Objects.TempBasal;
import com.gxwtech.roundtrip2.util.tools;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class TreatmentHistory extends AppCompatActivity {
    private static final String TAG = "TreatmentHistory";

    SectionsPagerAdapter mSectionsPagerAdapter;                                                     //will provide fragments for each of the sections
    ViewPager mViewPager;
    Fragment bolusFragmentObject;
    Fragment basalFragmentObject;
    BroadcastReceiver refreshTreatments;
    BroadcastReceiver refreshBasal;
    static Spinner numHours;
    static RealmManager realmManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment_history);

        realmManager = new RealmManager();

        //Num hours spinner setup
        numHours                    =   (Spinner) findViewById(R.id.integrationHours);
        String[] integrationHours   =   {"4", "8", "12", "24", "48"};
        ArrayAdapter<String> stringArrayAdapterHours= new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, integrationHours);
        numHours.setAdapter(stringArrayAdapterHours);
        numHours.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bolusFragment.update();
                basalFragment.update();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        numHours.setSelection(0);

        // Create the adapter that will return a fragment for each of the 4 primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) this.findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        bolusFragmentObject = new bolusFragment();
        basalFragmentObject = new basalFragment();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        realmManager.closeRealm();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (refreshTreatments != null){
            unregisterReceiver(refreshTreatments);
        }
        if (refreshBasal != null){
            unregisterReceiver(refreshBasal);
        }
    }

    @Override
    protected void onResume(){
        super.onResume();

        //Refresh the treatments list
        refreshTreatments = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                bolusFragment.update();
            }
        };
        registerReceiver(refreshTreatments, new IntentFilter("UPDATE_TREATMENTS"));
        bolusFragment.update();

        //Refresh the Basal list
        refreshBasal = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                basalFragment.update();
            }
        };
        registerReceiver(refreshBasal, new IntentFilter("UPDATE_BASAL"));
        basalFragment.update();
    }

    /* Page layout Fragments */

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            switch (position){
                case 0:
                    return bolusFragmentObject;
                case 1:
                    return basalFragmentObject;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Bolus Requests";
                case 1:
                    return "Basal Requests";
            }
            return null;
        }
    }

    public static class bolusFragment extends Fragment {
        public bolusFragment(){}
        private static ListView list;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_treatments_list, container, false);
            list    =   (ListView) rootView.findViewById(R.id.treatmentsFragmentList);

            update();
            return rootView;
        }

        public static void update(){

            if (list != null) {
                ArrayList<HashMap<String, String>> bolusList = new ArrayList<>();
                int hoursAgo                    =   Integer.getInteger(numHours.getSelectedItem().toString(),4);
                SimpleDateFormat sdfDateTime    =   new SimpleDateFormat("dd MMM HH:mm", MainApp.instance().getResources().getConfiguration().locale);
                List<Bolus> boluses             =   Bolus.getBolusesBetween(new Date(new Date().getTime() - ((60000 * 60 * hoursAgo))), new Date(), realmManager.getRealm());

                for (Bolus bolus : boluses){
                    HashMap<String, String> bolusItem = new HashMap<String, String>();
                    List<Integration> integrations = Integration.getIntegrationsFor("bolus", bolus.getId(), realmManager.getRealm());

                    bolusItem.put("id", bolus.getId());
                    bolusItem.put("object", "bolus");
                    bolusItem.put("value", bolus.getValue().toString());
                    bolusItem.put("timestamp", sdfDateTime.format(bolus.getTimestamp().getTime()));
                    bolusItem.put("type", bolus.getType());
                    for (Integration integration : integrations){
                        switch (integration.getType()){
                            case "aps_app":
                                bolusItem.put("integrationAPSState", integration.getState());
                                break;
                            case "pump":
                                bolusItem.put("integrationPumpState", integration.getState());
                                break;
                        }
                    }
                    bolusList.add(bolusItem);
                }

                mySimpleAdapter adapter = new mySimpleAdapter(MainApp.instance(), bolusList, R.layout.treatments_list_layout,
                        new String[]{"id", "object", "value", "timestamp", "type", "aps_app", "pump"},
                        new int[]{R.id.treatmentID, R.id.treatmentObject, R.id.treatmentAmount, R.id.treatmentTimestamp, R.id.treatmentType, R.id.treatmentAPS, R.id.treatmentResult});
                list.setAdapter(adapter);
            }
        }
    }

    public static class basalFragment extends Fragment {
        public basalFragment() {
        }

        private static ListView list;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_treatments_list, container, false);
            list = (ListView) rootView.findViewById(R.id.treatmentsFragmentList);

            update();
            return rootView;
        }

        public static void update() {
            if (list != null) {
                ArrayList<HashMap<String, String>> basalList = new ArrayList<>();
                Integer hoursAgo                    =   Integer.getInteger(numHours.getSelectedItem().toString(),4);
                SimpleDateFormat sdfDateTime    =   new SimpleDateFormat("dd MMM HH:mm", MainApp.instance().getResources().getConfiguration().locale);
                List<TempBasal> tempBasals      =   TempBasal.getTempBasalsDated(new Date(new Date().getTime() - ((60000 * 60 * hoursAgo))), new Date(), realmManager.getRealm());

                for (TempBasal tempBasal : tempBasals){
                    HashMap<String, String> basalItem = new HashMap<String, String>();
                    List<Integration> integrations = Integration.getIntegrationsFor("temp_basal", tempBasal.getId(), realmManager.getRealm());

                    basalItem.put("id", tempBasal.getId());
                    basalItem.put("object", "temp_basal");
                    basalItem.put("rate", tempBasal.getRate().toString());
                    basalItem.put("starttime", sdfDateTime.format(tempBasal.getStart_time().getTime()));
                    for (Integration integration : integrations){
                        switch (integration.getType()){
                            case "aps_app":
                                basalItem.put("integrationAPSState", integration.getState());
                                break;
                            case "pump":
                                basalItem.put("integrationPumpState", integration.getState());
                                break;
                        }
                    }
                    basalList.add(basalItem);
                }

                mySimpleAdapter adapter = new mySimpleAdapter(MainApp.instance(), basalList, R.layout.treatments_list_layout,
                        new String[]{"id", "object", "rate", "starttime", "aps_app", "pump"},
                        new int[]{R.id.treatmentID, R.id.treatmentObject, R.id.treatmentAmount, R.id.treatmentTimestamp, R.id.treatmentAPS, R.id.treatmentResult});
                list.setAdapter(adapter);
            }
        }
    }

    public static class mySimpleAdapter extends SimpleAdapter {

        public mySimpleAdapter(Context context, List<HashMap<String, String>> items, int resource, String[] from, int[] to) {
            super(context, items, resource, from, to);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            TextView value, type;
            type = (TextView) view.findViewById(R.id.treatmentObject);
            value = (TextView) view.findViewById(R.id.treatmentAmount);
            switch (type.getText().toString()){
                case "bolus":
                    value.setBackgroundResource(R.drawable.insulin_treatment_round);
                    value.setText(tools.formatDisplayInsulin(Double.valueOf(value.getText().toString()), 1));
                    value.setTextColor(MainApp.instance().getResources().getColor(R.color.primary_light));
                    break;
                case "temp_basal":
                    value.setBackgroundResource(R.drawable.insulin_basal_square);
                    value.setText(tools.formatDisplayBasal(Double.valueOf(value.getText().toString()),false));
                    value.setTextColor(MainApp.instance().getResources().getColor(R.color.primary_light));
                    break;
            }

            //Shows Pump result
            TextView textPump       = (TextView) view.findViewById(R.id.treatmentResult);
            ImageView imagePump     = (ImageView) view.findViewById(R.id.treatmentResultIcon);
            switch (textPump.getText().toString()) {
                case "to sync":
                    imagePump.setBackgroundResource(R.drawable.autorenew);
                    break;
                case "sent":
                    imagePump.setBackgroundResource(R.drawable.arrow_right_bold_circle);
                    break;
                case "received":
                    imagePump.setBackgroundResource(R.drawable.information);
                    break;
                case "delayed":
                    imagePump.setBackgroundResource(R.drawable.clock);
                    break;
                case "delivered":
                    imagePump.setBackgroundResource(R.drawable.checkbox_marked_circle);
                    break;
                case "error":
                    imagePump.setBackgroundResource(R.drawable.alert_circle);
                    break;
                default:
                    imagePump.setBackgroundResource(0);
                    break;
            }

            return view;
        }
    }
}
