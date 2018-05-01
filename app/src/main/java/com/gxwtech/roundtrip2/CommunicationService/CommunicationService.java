package com.gxwtech.roundtrip2.CommunicationService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.gxwtech.roundtrip2.CommunicationService.Objects.Bolus;
import com.gxwtech.roundtrip2.CommunicationService.Objects.DateDeserializer;
import com.gxwtech.roundtrip2.CommunicationService.Objects.Integration;
import com.gxwtech.roundtrip2.CommunicationService.Objects.IntegrationSerializer;
import com.gxwtech.roundtrip2.CommunicationService.Objects.RealmManager;
import com.gxwtech.roundtrip2.CommunicationService.Objects.TempBasal;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.util.Check;


import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;

/**
 * Created by Tim on 07/06/2016.
 * This service listens out for requests from HAPP and processes them
 */
public class CommunicationService extends android.app.Service {

    public CommunicationService(){}
    final static String TAG = "CommunicationService";

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            String action = "";
            Long requested = 0L;
            String safteyCheck = "";
            String pump = "";
            List<Integration> remoteIntegrations;
            List<String> remoteTreatments;
            Bundle data = new Bundle();
            RealmManager realmManager = new RealmManager();

            Log.d(TAG, "START");
            try {

                /*
                Expected Bundle data...
                "ACTION"                -   What is this incoming request? Example: "NEW_TREATMENTS"
                "DATE_REQUESTED"        -   When was this requested? So we can ignore old requests
                "PUMP"                  -   Name of the pump the APS expects this app to support
                "INTEGRATION_OBJECTS"   -   Array of Integration Objects, details of the objects being synced.  *OPTIONAL for NEW_TREATMENTS only*
                "TREATMENT_OBJECTS"     -   Array of Objects themselves being synced, TempBasal or Bolus        *OPTIONAL for NEW_TREATMENTS only*
                 */

                data = msg.getData();
                action = data.getString(RT2Const.commService.ACTION);
                requested = data.getLong(RT2Const.commService.DATE_REQUESTED, 0);
                pump = data.getString(RT2Const.commService.PUMP);
                Log.d("RECEIVED: ACTION", action);
                Log.d("RECEIVED: DATE", requested.toString());
                Log.d("RECEIVED: PUMP", pump);

            } catch (Exception e) {
                e.printStackTrace();
                // TODO: 16/01/2016 Issue getting treatment details from APS app msg for user
            }


            switch (action) {
                case RT2Const.commService.INCOMING_TEST_MSG:
                    Resources appR = MainApp.instance().getResources();
                    CharSequence txt = appR.getText(appR.getIdentifier("app_name", "string", MainApp.instance().getPackageName()));
                    Toast.makeText(MainApp.instance(), txt + ": Pump Driver App has connected successfully. ", Toast.LENGTH_LONG).show();
                    Log.d(TAG, txt + ": APS app has connected successfully.");

                    break;
                case RT2Const.commService.INCOMING_NEW_TREATMENTS:
                    GsonBuilder gsonBuilder = new GsonBuilder();
                    gsonBuilder.registerTypeAdapter(Date.class, new DateDeserializer());
                    Gson gson = gsonBuilder.create();

                    remoteIntegrations  = gson.fromJson(data.getString(RT2Const.commService.INTEGRATION_OBJECTS), new TypeToken<List<Integration>>() {}.getType());
                    remoteTreatments    = gson.fromJson(data.getString(RT2Const.commService.TREATMENT_OBJECTS), new TypeToken<List<String>>() {}.getType());
                    Log.d("RECEIVED: INTEGRATIONS", remoteIntegrations.toString());
                    Log.d("RECEIVED: TREATMENTS", remoteTreatments.toString());

                    for (int i = 0; i < remoteIntegrations.size(); i++) {

                        realmManager.getRealm().beginTransaction();

                        Integration integrationForAPS = remoteIntegrations.get(i);
                        integrationForAPS.setType           ("aps_app");
                        integrationForAPS.setState          ("received");
                        integrationForAPS.setToSync         (true);
                        integrationForAPS.setDate_updated   (new Date());
                        integrationForAPS.setRemote_id(remoteIntegrations.get(i).getLocal_object_id());

                        Integration integrationForPump = new Integration();
                        integrationForPump.setType          ("pump");
                        integrationForPump.setDate_updated  (new Date());
                        integrationForPump.setLocal_object(remoteIntegrations.get(i).getLocal_object());

                        String localObjectlID = "", localObjectState = "", localObjectDetails = "", rejectRequest = "";
                        if (!Check.isPumpSupported(pump))
                            rejectRequest += "Pump requested not supported. ";
                        if (Check.isRequestTooOld(requested)) rejectRequest += "Request too old. ";

                        switch (remoteIntegrations.get(i).getLocal_object()) {
                            case "temp_basal":
                                TempBasal tempBasal = gson.fromJson(remoteTreatments.get(i), TempBasal.class);
                                realmManager.getRealm().copyToRealm(tempBasal);
                                localObjectlID = tempBasal.getId();

                                switch (remoteIntegrations.get(i).getAction()) {
                                    case "new":
                                        rejectRequest += Check.isNewTempBasalSafe(tempBasal);
                                        if (rejectRequest.equals("")) // TODO: 12/08/2016 command to send TempBasal to pump
                                            break;
                                    case "cancel":
                                        rejectRequest += Check.isCancelTempBasalSafe(tempBasal, integrationForAPS, realmManager.getRealm());
                                        if (rejectRequest.equals("")) // TODO: 12/08/2016 command to send TempBasal to pump
                                            break;
                                }
                                break;

                            case "bolus_delivery":
                                Bolus bolus = gson.fromJson(remoteTreatments.get(i), Bolus.class);
                                realmManager.getRealm().copyToRealm(bolus);
                                localObjectlID = bolus.getId();
                                rejectRequest += Check.isBolusSafeToAction(bolus);

                                if (rejectRequest.equals("")) //TODO: 12/08/2016 command to action Bolus

                                    break;
                        }

                        if (rejectRequest.equals("")) {
                            //all ok
                            localObjectState = "received";
                            localObjectDetails = "Request sent to pump";
                        } else {
                            //reject
                            localObjectState = "error";
                            localObjectDetails = rejectRequest;
                        }

                        integrationForAPS.setLocal_object_id(localObjectlID);
                        integrationForAPS.setState(localObjectState);
                        integrationForAPS.setDetails(localObjectDetails);
                        realmManager.getRealm().copyToRealm(integrationForAPS);
                        integrationForPump.setLocal_object_id(localObjectlID);
                        integrationForPump.setState(localObjectState);
                        integrationForPump.setDetails(localObjectDetails);
                        realmManager.getRealm().copyToRealm(integrationForPump);
                        realmManager.getRealm().commitTransaction();
                    }
                    break;

                default:
                    Log.e(TAG, "handleMessage: Unknown Action: " + action);
            }

            connect_to_aps_app();
            realmManager.closeRealm();
        }
    }



    final Messenger myMessenger = new Messenger(new IncomingHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return myMessenger.getBinder();
    }


    public void updateAPSApp(){

        RealmManager realmManager = new RealmManager();
        List<Integration> integrations = Integration.getIntegrationsToSync("aps_app", null, realmManager.getRealm());

        if (integrations.size() > 0) {
            /*
                Bundle data...
                "ACTION"                -   What is this incoming request? Example: "TREATMENT_UPDATES"
                "INTEGRATION_OBJECTS"   -   Array of Integration Objects, details of the objects being synced.  *OPTIONAL for UPDATE_TREATMENTS only*
            */

            Log.d(TAG, "UPDATE APS App:" + integrations.size() + " treatments to update");
            Log.d(TAG, "INTEGRATIONS:" + integrations.toString());
            Message msg = Message.obtain();
            boolean updateOK = true;
            try {
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Class.forName("io.realm.IntegrationRealmProxy"), new IntegrationSerializer())
                        .create();

                Bundle bundle = new Bundle();
                bundle.putString(RT2Const.commService.ACTION, RT2Const.commService.OUTGOING_TREATMENT_UPDATES);
                bundle.putString(RT2Const.commService.INTEGRATION_OBJECTS, gson.toJson(integrations));
                msg.setData(bundle);

            } catch (ClassNotFoundException e){
                updateOK = false;
                Log.e(TAG, "Error creating gson object: " + e.getLocalizedMessage());
            }

            try {
                myService.send(msg);
                Log.d(TAG, integrations.size() + " updates sent");
            } catch (RemoteException e) {
                updateOK = false;
                Log.e(TAG, integrations.size() + " updates failed. " + e.getLocalizedMessage());
            }

            for (Integration integration : integrations){
                realmManager.getRealm().beginTransaction();
                if (updateOK) {
                    integration.setState("sent");
                } else {
                    integration.setState("error");
                    integration.setDetails("Update to APS failed. Will not be resent.");
                }
                integration.setToSync(false);
                realmManager.getRealm().commitTransaction();
            }
        }

        try {
            if (isBound) CommunicationService.this.unbindService(myConnection);
        } catch (IllegalArgumentException e) {
            //catch if service was killed in a unclean way
        }

        realmManager.closeRealm();
    }

    //Connect to the APS App Treatments Service
    private void connect_to_aps_app(){
        // TODO: 16/06/2016 should be able to pick the APS app from UI not hardcoded 
        Intent intent = new Intent("com.hypodiabetic.happ.services.TreatmentService");
        intent.setPackage("com.hypodiabetic.happ");
        CommunicationService.this.bindService(intent, myConnection, Context.BIND_AUTO_CREATE);
    }
    //Our Service that APS App will connect to
    private Messenger myService = null;
    private Boolean isBound = false;
    private ServiceConnection myConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            myService = new Messenger(service);
            isBound = true;

            updateAPSApp();
        }

        public void onServiceDisconnected(ComponentName className) {
            myService = null;
            isBound = false;
        }
    };


}

