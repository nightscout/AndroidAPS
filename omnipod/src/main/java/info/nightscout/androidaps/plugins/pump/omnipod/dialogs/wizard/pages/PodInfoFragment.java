package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.tech.freak.wizardpager.model.ReviewItem;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;


/**
 * Created by andy on 12/11/2019
 */
public class PodInfoFragment extends DaggerFragment {
    private static final String ARG_KEY = "key";
    private static final String ARG_INIT_POD = "initPod";

    @Inject OmnipodUtil omnipodUtil;
    @Inject PodStateManager podStateManager;

    private PageFragmentCallbacks mCallbacks;
    private String mKey;
    public boolean isInitPod;
    private ArrayList<ReviewItem> mCurrentReviewItems;

    public static PodInfoFragment create(String key, boolean initPod) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putBoolean(ARG_INIT_POD, initPod);

        PodInfoFragment fragment = new PodInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("SourceLockedOrientationActivity") @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Bundle args = getArguments();
        mKey = args.getString(ARG_KEY);
        isInitPod = args.getBoolean(ARG_INIT_POD);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.omnipod_initpod_pod_info, container, false);

        TextView titleView = rootView.findViewById(R.id.podInfoTitle);
        titleView.setText(R.string.omnipod_init_pod_wizard_pod_info_title);
        titleView.setTextColor(getResources().getColor(com.tech.freak.wizardpager.R.color.review_green));

        TextView headerText = rootView.findViewById(R.id.podInfoText);
        headerText.setText(isInitPod ? //
                R.string.omnipod_init_pod_wizard_pod_info_init_pod_description : //
                R.string.omnipod_init_pod_wizard_pod_info_remove_pod_description);

        if (isInitPod) {
            if (createDataOfPod()) {

                ListView listView = (ListView) rootView.findViewById(R.id.podInfoList);
                listView.setAdapter(new PodInfoAdapter(mCurrentReviewItems, getContext()));
                listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            }
        }

        return rootView;
    }

    private boolean createDataOfPod() {
        if (podStateManager == null)
            return false;

        mCurrentReviewItems = new ArrayList<>();
        mCurrentReviewItems.add(new ReviewItem("Pod Address", "" + podStateManager.getAddress(), "33"));
        mCurrentReviewItems.add(new ReviewItem("Activated At", podStateManager.getActivatedAt() == null ? "Not activated yet" : podStateManager.getActivatedAt().toString("dd.MM.yyyy HH:mm:ss"), "34"));
        if (podStateManager.getLot() != null) {
            mCurrentReviewItems.add(new ReviewItem("LOT", "" + podStateManager.getLot(), "35"));
        }
        if (podStateManager.getTid() != null) {
            mCurrentReviewItems.add(new ReviewItem("TID", "" + podStateManager.getLot(), "36"));
        }
        if (podStateManager.getPiVersion() != null) {
            mCurrentReviewItems.add(new ReviewItem("Pi Version", podStateManager.getPiVersion().toString(), "37"));
        }
        if (podStateManager.getPmVersion() != null) {
            mCurrentReviewItems.add(new ReviewItem("Pm Version", podStateManager.getPmVersion().toString(), "38"));
        }

        return true;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        FragmentActivity activity = getActivity();

        if (!(activity instanceof PageFragmentCallbacks)) {
            throw new ClassCastException("Activity must implement PageFragmentCallbacks");
        }

        mCallbacks = (PageFragmentCallbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    private class PodInfoAdapter extends ArrayAdapter<ReviewItem> {

        private ArrayList<ReviewItem> dataSet;
        Context mContext;
        private int lastPosition = -1;

        // View lookup cache

        public PodInfoAdapter(ArrayList<ReviewItem> data, Context context) {
            super(context, com.tech.freak.wizardpager.R.layout.list_item_review, data);
            this.dataSet = data;
            this.mContext = context;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            ReviewItem dataModel = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            ViewHolder viewHolder; // view lookup cache stored in tag

            final View result;

            if (convertView == null) {

                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.omnipod_initpod_pod_info_item, parent, false);
                viewHolder.txtName = (TextView) convertView.findViewById(android.R.id.text1);
                viewHolder.txtType = (TextView) convertView.findViewById(android.R.id.text2);

                result = convertView;

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
                result = convertView;
            }

            viewHolder.txtName.setText(dataModel.getTitle());
            viewHolder.txtType.setText(dataModel.getDisplayValue());

            // Return the completed view to render on screen
            return convertView;
        }
    }

    private static class ViewHolder {
        TextView txtName;
        TextView txtType;
    }

}
