package com.gxwtech.roundtrip2.ServiceMessageViewActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import com.gxwtech.roundtrip2.R;
import com.gxwtech.roundtrip2.RT2Const;

import java.util.List;

/**
 * An activity representing a list of ServiceMessageViews. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ServiceMessageViewDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ServiceMessageViewListActivity extends AppCompatActivity {
    private static final String TAG = "SvcMsgViewListActivity";
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private BroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servicemessageview_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        View recyclerView = findViewById(R.id.servicemessageview_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.servicemessageview_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (RT2Const.IPC.MSG_ServiceCommand.equals(action) ||
                        (RT2Const.IPC.MSG_ServiceResult.equals(action))) {
                    Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);
                    // build ID from the millisecond timestamp and a descriptive part of the ServiceMessage
                    String command = bundle.getString("command");
                    String serviceResultType = bundle.getString("ServiceResultType");
                    String idPart = "(null)";
                    if (command != null) {
                        String commandID = bundle.getString("commandID");
                        if (commandID != null) {
                            idPart = command + commandID;
                        } else {
                            idPart = command;
                        }
                    }
                    if (serviceResultType != null) {
                        idPart = serviceResultType;
                    }
                    String messageID = bundle.getString(RT2Const.IPC.instantKey,"(null)") + idPart;
                    // build content from the descriptive string
                    String content = idPart;
                    // build details from the KV pairs from the message
                    StringBuilder detailsBuilder = new StringBuilder();
                    for (String key : bundle.keySet()) {
                        if ("command".equals(key)) continue;
                        if ("ServiceResultType".equals(key)) continue;
                        if ("instant".equals(key)) continue;
                        detailsBuilder.append(key);
                        detailsBuilder.append("=");
                        String stringValue = bundle.getString(key);
                        if (stringValue != null) {
                            detailsBuilder.append(stringValue);
                        } else {
                            Bundle bundleValue = bundle.getBundle(key);
                            if (bundleValue != null) {
                                detailsBuilder.append("(bundle)");
                            } else {
                                detailsBuilder.append("(?)");
                            }
                        }
                        detailsBuilder.append("\n");
                    }
                    ServiceMessageView.addItem(new ServiceMessageViewItem(messageID,content,detailsBuilder.toString()));
                } else {
                    Log.e(TAG,"onReceive: unhandled action: " + action);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RT2Const.IPC.MSG_ServiceCommand);
        intentFilter.addAction(RT2Const.IPC.MSG_ServiceResult);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(ServiceMessageView.ITEMS));
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<ServiceMessageViewItem> mValues;

        public SimpleItemRecyclerViewAdapter(List<ServiceMessageViewItem> items) {
            mValues = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.servicemessageview_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mValues.get(position);
            holder.mIdView.setText(mValues.get(position).id);
            holder.mContentView.setText(mValues.get(position).content);

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putString(ServiceMessageViewDetailFragment.ARG_ITEM_ID, holder.mItem.id);
                        ServiceMessageViewDetailFragment fragment = new ServiceMessageViewDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.servicemessageview_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, ServiceMessageViewDetailActivity.class);
                        intent.putExtra(ServiceMessageViewDetailFragment.ARG_ITEM_ID, holder.mItem.id);

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
            public ServiceMessageViewItem mItem;

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
