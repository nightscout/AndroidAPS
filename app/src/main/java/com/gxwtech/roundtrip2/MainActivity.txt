package com.gxwtech.roundtrip2;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;

import com.gxwtech.roundtrip2.HistoryActivity.HistoryPageListActivity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ListView;


import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.ServiceData.BasalProfile;
import com.gxwtech.roundtrip2.ServiceData.BolusWizardCarbProfile;
import com.gxwtech.roundtrip2.ServiceData.ISFProfile;
import com.gxwtech.roundtrip2.ServiceData.PumpModelResult;
import com.gxwtech.roundtrip2.ServiceData.ReadPumpClockResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceClientActions;
import com.gxwtech.roundtrip2.ServiceData.ServiceCommand;
import com.gxwtech.roundtrip2.ServiceData.ServiceNotification;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;
import com.gxwtech.roundtrip2.ServiceMessageViewActivity.ServiceMessageViewListActivity;
import com.gxwtech.roundtrip2.util.tools;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 2177; // just something unique.
    private RoundtripServiceClientConnection roundtripServiceClientConnection;
    private BroadcastReceiver mBroadcastReceiver;

    BroadcastReceiver apsAppConnected;
    Bundle storeForHistoryViewer;

    //UI items
    private DrawerLayout mDrawerLayout;
    private LinearLayout mDrawerLinear;
    private Toolbar toolbar;

    public static Context mContext;  // TODO: 09/07/2016 @TIM this should not be needed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.w(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupMenuAndToolbar();

        mContext = this; // TODO: 09/07/2016 @TIM this should not be needed

        //Sets default Preferences
        PreferenceManager.setDefaultValues(this, R.xml.pref_pump, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_rileylink, false);

        setBroadcastReceiver();




        /* start the RoundtripService */
        /* using startService() will keep the service running until it is explicitly stopped
         * with stopService() or by RoundtripService calling stopSelf().
         * Note that calling startService repeatedly has no ill effects on RoundtripService
         */
        // explicitly call startService to keep it running even when the GUI goes away.
        Intent bindIntent = new Intent(this,RoundtripService.class);
        startService(bindIntent);

        linearProgressBar = (ProgressBar)findViewById(R.id.progressBarCommandActivity);
        spinnyProgressBar = (ProgressBar)findViewById(R.id.progressBarSpinny);
    }


    @Override
    protected void onResume(){
        super.onResume();

        setBroadcastReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (apsAppConnected != null){
            LocalBroadcastManager.getInstance(MainApp.instance()).unregisterReceiver(apsAppConnected);
        }
        if (mBroadcastReceiver != null){
            LocalBroadcastManager.getInstance(MainApp.instance()).unregisterReceiver(mBroadcastReceiver);
        }
    }

    public void setBroadcastReceiver() {
        //Register this receiver for UI Updates
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent receivedIntent) {

                if (receivedIntent == null) {
                    Log.e(TAG, "onReceive: received null intent");
                } else {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApp.instance());
                    ServiceTransport transport;

                    switch (receivedIntent.getAction()) {
                        case RT2Const.local.INTENT_serviceConnected:
                        case RT2Const.local.INTENT_NEW_rileylinkAddressKey:
                            showIdle();
                            /**
                             * Client MUST send a "UseThisRileylink" message because it asserts that
                             * the user has given explicit permission to use bluetooth.
                             *
                             * We can change the format so that it is a simple "bluetooth OK" message,
                             * rather than an explicit address of a Rileylink, and the Service can
                             * use the last known good value.  But the kick-off of bluetooth ops must
                             * come from an Activity.
                             */
                            String RileylinkBLEAddress = prefs.getString(RT2Const.serviceLocal.rileylinkAddressKey, "");
                            if (RileylinkBLEAddress.equals("")){
                                // TODO: 11/07/2016 @TIM UI message for user
                                Log.e(TAG, "No Rileylink BLE Address saved in app");
                            } else {
                                showBusy("Configuring Service", 50);
                                MainApp.getServiceClientConnection().setThisRileylink(RileylinkBLEAddress);
                            }
                            break;
                        case RT2Const.local.INTENT_NEW_pumpIDKey:
                            MainApp.getServiceClientConnection().sendPUMP_useThisDevice(prefs.getString(RT2Const.serviceLocal.pumpIDKey, ""));
                            break;
                        case RT2Const.local.INTENT_historyPageViewerReady:
                            Intent sendHistoryIntent = new Intent(RT2Const.local.INTENT_historyPageBundleIncoming);
                            sendHistoryIntent.putExtra(RT2Const.IPC.MSG_PUMP_history_key, storeForHistoryViewer);
                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(sendHistoryIntent);
                            break;
                        case RT2Const.IPC.MSG_ServiceResult:
                            Log.i(TAG, "Received ServiceResult");

                            Bundle bundle = receivedIntent.getBundleExtra(RT2Const.IPC.bundleKey);
                            transport = new ServiceTransport(bundle);
                            if (transport.commandDidCompleteOK()) {
                                String originalCommandName = transport.getOriginalCommandName();
                                switch (originalCommandName) {
                                    case "ReadPumpModel":
                                        PumpModelResult modelResult = new PumpModelResult();
                                        modelResult.initFromServiceResult(transport.getServiceResult());
                                        String pumpModelString = modelResult.getPumpModel();
                                        // GGW Tue Jul 12 02:29:54 UTC 2016: ok, now what do we do with the pump model?
                                        showIdle();
                                        break;
                                    case "ReadPumpClock":
                                        ReadPumpClockResult clockResult = new ReadPumpClockResult();
                                        clockResult.initFromServiceResult(transport.getServiceResult());
                                        TextView pumpTimeTextView = (TextView) findViewById(R.id.textViewPumpClockTime);
                                        pumpTimeTextView.setText(clockResult.getTimeString());
                                        showIdle();
                                        break;
                                    case "FetchPumpHistory":
                                        storeForHistoryViewer = receivedIntent.getExtras().getBundle(RT2Const.IPC.bundleKey);
                                        startActivity(new Intent(context, HistoryPageListActivity.class));
                                        // wait for history viewer to announce "ready"
                                        showIdle();
                                        break;
                                    case "RetrieveHistoryPage":
                                        storeForHistoryViewer = receivedIntent.getExtras().getBundle(RT2Const.IPC.bundleKey);
                                        startActivity(new Intent(context, HistoryPageListActivity.class));
                                        // wait for history viewer to announce "ready"
                                        showIdle();
                                        break;
                                    case "ISFProfile":
                                        ISFProfile isfProfile = new ISFProfile();
                                        isfProfile.initFromServiceResult(transport.getServiceResult());
                                        // TODO: do something with isfProfile
                                        showIdle();
                                        break;
                                    case "BasalProfile":
                                        BasalProfile basalProfile = new BasalProfile();
                                        basalProfile.initFromServiceResult(transport.getServiceResult());
                                        // TODO: do something with basal profile
                                        showIdle();
                                        break;
                                    case "BolusWizardCarbProfile":
                                        BolusWizardCarbProfile carbProfile = new BolusWizardCarbProfile();
                                        carbProfile.initFromServiceResult(transport.getServiceResult());
                                        // TODO: do something with carb profile
                                        showIdle();
                                        break;
                                    case "UpdatePumpStatus":
                                        // rebroadcast for HAPP

                                        break;
                                    default:
                                        Log.e(TAG, "Dunno what to do with this command completion: " + transport.getOriginalCommandName());
                                }
                            } else {
                                Log.e(TAG,"Command failed? " + transport.getOriginalCommandName());
                            }
                            break;
                        case RT2Const.IPC.MSG_ServiceNotification:
                            transport = new ServiceTransport(receivedIntent.getBundleExtra(RT2Const.IPC.bundleKey));
                            ServiceNotification notification = transport.getServiceNotification();
                            String note = notification.getNotificationType();
                            switch (note) {
                                case RT2Const.IPC.MSG_BLE_RileyLinkReady:
                                    setRileylinkStatusMessage("OK");
                                    break;
                                case RT2Const.IPC.MSG_PUMP_pumpFound:
                                    setPumpStatusMessage("OK");
                                    break;
                                case RT2Const.IPC.MSG_PUMP_pumpLost:
                                    setPumpStatusMessage("Lost");
                                    break;
                                case RT2Const.IPC.MSG_note_WakingPump:
                                    showBusy("Waking Pump", 99);
                                    break;
                                case RT2Const.IPC.MSG_note_FindingRileyLink:
                                    showBusy("Finding RileyLink", 99);
                                    break;
                                case RT2Const.IPC.MSG_note_Idle:
                                    showIdle();
                                    break;
                                default:
                                    Log.e(TAG, "Unrecognized Notification: '" + note + "'");
                            }
                            break;
                        default:
                            Log.e(TAG, "Unrecognized intent action: " + receivedIntent.getAction());
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RT2Const.local.INTENT_serviceConnected);
        intentFilter.addAction(RT2Const.IPC.MSG_ServiceResult);
        intentFilter.addAction(RT2Const.IPC.MSG_ServiceNotification);
        intentFilter.addAction(RT2Const.local.INTENT_historyPageViewerReady);


        linearProgressBar = (ProgressBar)findViewById(R.id.progressBarCommandActivity);
        spinnyProgressBar = (ProgressBar)findViewById(R.id.progressBarSpinny);
        LocalBroadcastManager.getInstance(MainApp.instance()).registerReceiver(mBroadcastReceiver, intentFilter);
    }






    /**
     *
     *  GUI element functions
     *
     */


    private int mProgress = 0;
    private int mSpinnyProgress = 0;
    private ProgressBar linearProgressBar;
    private ProgressBar spinnyProgressBar;
    private static final int spinnyFPS = 10;
    private Thread spinnyThread;
    void showBusy(String activityString, int progress) {
        mProgress = progress;
        TextView tv = (TextView)findViewById(R.id.textViewActivity);
        tv.setText(activityString);
        linearProgressBar.setProgress(progress);
        if (progress > 0) {
            spinnyProgressBar.setVisibility(View.VISIBLE);
            if (spinnyThread == null) {
                spinnyThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while ((mProgress > 0) && (mProgress < 100)) {
                            mSpinnyProgress += 100 / spinnyFPS;
                            spinnyProgressBar.setProgress(mSpinnyProgress);
                            SystemClock.sleep(1000 / spinnyFPS);
                        }
                        spinnyThread = null;
                    }
                });
                spinnyThread.start();
            }
        } else {
            spinnyProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    void showIdle() {
        showBusy("Idle",0);
    }

    void setRileylinkStatusMessage(String statusMessage) {
        TextView field = (TextView)findViewById(R.id.textViewFieldRileyLink);
        field.setText(statusMessage);
    }

    void setPumpStatusMessage(String statusMessage) {
        TextView field = (TextView)findViewById(R.id.textViewFieldPump);
        field.setText(statusMessage);
    }

    public void onTunePumpButtonClicked(View view) {
        MainApp.getServiceClientConnection().doTunePump();
    }

    public void onFetchHistoryButtonClicked(View view) {
        /* does not work. Crashes sig 11 */
        showBusy("Fetch history page 0",50);
        MainApp.getServiceClientConnection().doFetchPumpHistory();
    }

    public void onFetchSavedHistoryButtonClicked(View view) {
        showBusy("Fetching history (not saved)",50);
        MainApp.getServiceClientConnection().doFetchSavedHistory();
    }

    public void onReadPumpClockButtonClicked(View view) {
        showBusy("Reading Pump Clock",50);
        MainApp.getServiceClientConnection().readPumpClock();
    }

    public void onGetISFProfileButtonClicked(View view) {
        //ServiceCommand getISFProfileCommand = ServiceClientActions.makeReadISFProfileCommand();
        //roundtripServiceClientConnection.sendServiceCommand(getISFProfileCommand);
        MainApp.getServiceClientConnection().readISFProfile();
    }

    public void onViewEventLogButtonClicked(View view) {
        startActivity(new Intent(getApplicationContext(),ServiceMessageViewListActivity.class));
    }

    public void onUpdateAllStatusButtonClicked(View view) {
        MainApp.getServiceClientConnection().updateAllStatus();
    }

    public void onGetCarbProfileButtonClicked(View view) {
        showBusy("Getting Carb Profile",1);
        roundtripServiceClientConnection.sendServiceCommand(ServiceClientActions.makeReadBolusWizardCarbProfileCommand());
    }

    /* UI Setup */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)){
                    mDrawerLayout.closeDrawers();
                } else {
                    mDrawerLayout.openDrawer(GravityCompat.START);
                }
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(mDrawerLinear);
                return true;

            default:
                return true;
        }
    }

    public void setupMenuAndToolbar() {
        //Setup menu
        mDrawerLayout                   = (DrawerLayout)findViewById(R.id.drawer_layout);
        mDrawerLinear                   = (LinearLayout) findViewById(R.id.left_drawer);
        toolbar                         = (Toolbar) findViewById(R.id.mainActivityToolbar);
        Drawable logsIcon               = getDrawable(R.drawable.file_chart);
        Drawable historyIcon            = getDrawable(R.drawable.history);
        Drawable settingsIcon           = getDrawable(R.drawable.settings);
        Drawable catIcon                = getDrawable(R.drawable.cat);
        Drawable apsIcon                = getDrawable(R.drawable.refresh);

        logsIcon.setColorFilter(getResources().getColor(R.color.primary_dark), PorterDuff.Mode.SRC_ATOP);
        historyIcon.setColorFilter(getResources().getColor(R.color.primary_dark), PorterDuff.Mode.SRC_ATOP);
        settingsIcon.setColorFilter(getResources().getColor(R.color.primary_dark), PorterDuff.Mode.SRC_ATOP);
        catIcon.setColorFilter(getResources().getColor(R.color.primary_dark), PorterDuff.Mode.SRC_ATOP);
        apsIcon.setColorFilter(getResources().getColor(R.color.primary_dark), PorterDuff.Mode.SRC_ATOP);

        ListView mDrawerList            = (ListView)findViewById(R.id.navList);
        ArrayList<NavItem> menuItems    = new ArrayList<>();
        menuItems.add(new NavItem("APS Integration", apsIcon));
        menuItems.add(new NavItem("Pump History", historyIcon));
        menuItems.add(new NavItem("Treatment Logs", logsIcon));
        menuItems.add(new NavItem("Settings", settingsIcon));
        menuItems.add(new NavItem("View LogCat", catIcon));
        DrawerListAdapter adapterMenu = new DrawerListAdapter(this, menuItems);
        mDrawerList.setAdapter(adapterMenu);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        //Check APS App Connectivity
                        sendAPSAppMessage(view);
                        break;
                    case 1:
                        //Pump History
                        startActivity(new Intent(getApplicationContext(), HistoryPageListActivity.class));
                        break;
                    case 2:
                        //Treatment Logs
                        startActivity(new Intent(getApplicationContext(), TreatmentHistory.class));
                        break;
                    case 3:
                        //Settings
                        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                        break;
                    case 4:
                        //View LogCat
                        tools.showLogging();
                        break;
                }
                mDrawerLayout.closeDrawers();
            }
        });

        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,R.string.drawer_open, R.string.drawer_close) {
            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()

                //Insulin Integration App, try and connect
                //checkInsulinAppIntegration(false);
            }
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mDrawerToggle.syncState();
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }


    /* Functions for APS App Service */

    //Our Service that APS App will connect to
    private Messenger myService = null;
    private ServiceConnection myConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            myService = new Messenger(service);

            //Broadcast there has been a connection
            Intent intent = new Intent("APS_CONNECTED");
            LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);
        }

        public void onServiceDisconnected(ComponentName className) {
            myService = null;
            //FYI, only called if Service crashed or was killed, not on unbind
        }
    };

    public void sendAPSAppMessage(final View view)
    {
        //listen out for a successful connection
        apsAppConnected = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Resources appR = view.getContext().getResources();
                CharSequence txt = appR.getText(appR.getIdentifier("app_name", "string", view.getContext().getPackageName()));

                Message msg = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putString(RT2Const.commService.ACTION,RT2Const.commService.OUTGOING_TEST_MSG);
                bundle.putString(RT2Const.commService.REMOTE_APP_NAME, txt.toString());
                msg.setData(bundle);

                try {
                    myService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    //cannot Bind to service
                    Snackbar snackbar = Snackbar
                            .make(view, "error sending msg: " + e.getMessage(), Snackbar.LENGTH_INDEFINITE);
                    snackbar.show();
                }

                if (apsAppConnected != null) LocalBroadcastManager.getInstance(MainApp.instance()).unregisterReceiver(apsAppConnected); //Stop listening for new connections
                MainApp.instance().unbindService(myConnection);
            }
        };
        LocalBroadcastManager.getInstance(MainApp.instance()).registerReceiver(apsAppConnected, new IntentFilter("APS_CONNECTED"));

        connect_to_aps_app(MainApp.instance());
    }

    //Connect to the APS App Treatments Service
    private void connect_to_aps_app(Context c){
        // TODO: 16/06/2016 add user selected aps app
        Intent intent = new Intent("com.hypodiabetic.happ.services.TreatmentService");
        intent.setPackage("com.hypodiabetic.happ");
        c.bindService(intent, myConnection, Context.BIND_AUTO_CREATE);
    }



}

class NavItem {
    String mTitle;
    Drawable mIcon;

    public NavItem(String title, Drawable icon) {
        mTitle = title;
        mIcon = icon;
    }
}

class DrawerListAdapter extends BaseAdapter {

    Context mContext;
    ArrayList<NavItem> mNavItems;

    public DrawerListAdapter(Context context, ArrayList<NavItem> navItems) {
        mContext = context;
        mNavItems = navItems;
    }

    @Override
    public int getCount() {
        return mNavItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mNavItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.menu_item, null);
        }
        else {
            view = convertView;
        }

        TextView titleView = (TextView) view.findViewById(R.id.menuText);
        ImageView iconView = (ImageView) view.findViewById(R.id.menuIcon);

        titleView.setText( mNavItems.get(position).mTitle);
        iconView.setBackground(mNavItems.get(position).mIcon);
        return view;
    }
}
