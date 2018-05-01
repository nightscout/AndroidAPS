package com.gxwtech.roundtrip2.HistoryActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gxwtech.roundtrip2.R;
import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.ServiceData.RetrieveHistoryPageResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An activity representing a list of HistoryPages. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link HistoryPageDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class HistoryPageListActivity extends AppCompatActivity {
    private static final String TAG = "HistoryPageListActivity";


    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private BroadcastReceiver mBroadcastRecevier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historypage_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_pump_history);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        View recyclerView = findViewById(R.id.historypage_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.historypage_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        mBroadcastRecevier = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent receivedIntent) {
                if (receivedIntent == null) {
                    Log.e(TAG,"onReceive: received null intent");
                } else {
                    String action = receivedIntent.getAction();
                    if (action == null) {
                        Log.e(TAG, "onReceive: null action");
                    } else {
                        if (RT2Const.local.INTENT_historyPageBundleIncoming.equals(action)) {
                            Bundle incomingBundle = receivedIntent.getExtras().getBundle(RT2Const.IPC.MSG_PUMP_history_key);
                            ServiceTransport transport = new ServiceTransport(incomingBundle);
                            ServiceResult result = transport.getServiceResult();
                            if ("RetrieveHistoryPageResult".equals(result.getServiceResultType())) {
                                RetrieveHistoryPageResult pageResult = (RetrieveHistoryPageResult) result;
                                Bundle page = pageResult.getPageBundle();
                                ArrayList<Bundle> recordBundleList = page.getParcelableArrayList("mRecordList");
                                try {
                                    for (Bundle record : recordBundleList) {
                                        HistoryPageListContent.addItem(record);
                                    }
                                } catch (java.lang.NullPointerException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            Log.e(TAG,"Unrecognized intent action: "+action);
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(RT2Const.local.INTENT_historyPageBundleIncoming);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mBroadcastRecevier,filter);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bluetooth_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miScan:
                getHistory();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void getHistory(){
        // tell them we're ready for data
        Intent intent = new Intent(RT2Const.local.INTENT_historyPageViewerReady);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(HistoryPageListContent.ITEMS));
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<HistoryPageListContent.RecordHolder> mValues;

        public SimpleItemRecyclerViewAdapter(List<HistoryPageListContent.RecordHolder> items) {
            mValues = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.historypage_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mValues.get(position);
            holder.mIdView.setText(mValues.get(position).dateAndName);
            String keytext = "";
            Set<String> keys = holder.mItem.content.keySet();
            int n = 0;
            for (String key : keys) {
                if (!key.equals("_type") && !key.equals("_stype") && !key.equals("timestamp") && !key.equals("_opcode")) {
                    try {
                        keytext += key + ":" + holder.mItem.content.get(key).toString();
                        n++;
                        if (n < keys.size() - 1) {
                            keytext += "\n";
                        }
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }
            holder.mContentView.setText(keytext);

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putString(HistoryPageDetailFragment.ARG_ITEM_ID, holder.mItem.id);
                        HistoryPageDetailFragment fragment = new HistoryPageDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.historypage_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, HistoryPageDetailActivity.class);
                        intent.putExtra(HistoryPageDetailFragment.ARG_ITEM_ID, holder.mItem.id);

                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mIdView;
            public final TextView mContentView;
            public HistoryPageListContent.RecordHolder mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mIdView = (TextView) view.findViewById(R.id.id);
                mContentView = (TextView) view.findViewById(R.id.content);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }
}
