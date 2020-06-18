package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.atech.android.library.wizardpager.util.WizardPagesUtil;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;

/**
 * Created by andy on 12/11/2019
 */
public class InitActionFragment extends Fragment implements PodInitReceiver {
    private static final String ARG_KEY = "key";

    protected PageFragmentCallbacks mCallbacks;
    protected String mKey;
    protected InitActionPage mPage;

    protected ProgressBar progressBar;
    protected TextView errorView;
    protected Button retryButton;

    protected PodInitActionType podInitActionType;
    protected List<PodInitActionType> children;
    protected Map<PodInitActionType, CheckBox> mapCheckBoxes;
    protected InitActionFragment instance;

    protected PumpEnactResult callResult;


    public static InitActionFragment create(String key, PodInitActionType podInitActionType) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);

        InitActionFragment fragment = new InitActionFragment();
        fragment.setArguments(args);
        fragment.setPodInitActionType(podInitActionType);
        return fragment;
    }

    public InitActionFragment() {
        this.instance = this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mKey = args.getString(ARG_KEY);
        mPage = (InitActionPage) mCallbacks.onGetPage(mKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.omnipod_initpod_init_action, container, false);
        WizardPagesUtil.setTitle(mPage, rootView);

        this.progressBar = rootView.findViewById(R.id.initAction_progressBar);
        this.errorView = rootView.findViewById(R.id.initAction_textErrorMessage);

        TextView headerView = rootView.findViewById(R.id.initAction_header);

        LinearLayout linearLayout = rootView.findViewById(R.id.initAction_ItemsHolder);

        children = podInitActionType.getChildren();
        mapCheckBoxes = new HashMap<>();

        for (PodInitActionType child : children) {

            CheckBox checkBox1 = new CheckBox(getContext());
            checkBox1.setText(child.getResourceId());
            checkBox1.setClickable(false);
            checkBox1.setTextAppearance(R.style.WizardPagePodListItem);
            checkBox1.setHeight(120);
            checkBox1.setTextSize(15);
            checkBox1.setTextColor(headerView.getTextColors().getDefaultColor());

            linearLayout.addView(checkBox1);

            mapCheckBoxes.put(child, checkBox1);
        }

        if (podInitActionType == PodInitActionType.FillCannulaSetBasalProfileWizardStep) {
            headerView.setText(R.string.omnipod_init_pod_wizard_step4_action_header);
        } else if (podInitActionType == PodInitActionType.DeactivatePodWizardStep) {
            headerView.setText(R.string.omnipod_remove_pod_wizard_step2_action_header);
        }

        this.retryButton = rootView.findViewById(R.id.initAction_RetryButton);

        this.retryButton.setOnClickListener(view -> {

            getActivity().runOnUiThread(() -> {
                for (PodInitActionType actionType : mapCheckBoxes.keySet()) {
                    mapCheckBoxes.get(actionType).setChecked(false);
                    mapCheckBoxes.get(actionType).setTextColor(headerView.getTextColors().getDefaultColor());
                }
            });

            new InitPodTask(instance).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

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


    public PodInitActionType getPodInitActionType() {
        return podInitActionType;
    }


    public void setPodInitActionType(PodInitActionType podInitActionType) {
        this.podInitActionType = podInitActionType;
    }


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        //System.out.println("ACTION: setUserVisibleHint="+ isVisibleToUser);
        if (isVisibleToUser) {
            //System.out.println("ACTION: Visible");
            new InitPodTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            System.out.println("ACTION: Not visible");
        }
    }

    public void actionOnReceiveResponse(String result) {
//        System.out.println("ACTION: actionOnReceiveResponse: " + result);
//
//        boolean isOk = callResult.success;
//
//        progressBar.setVisibility(View.GONE);
//
//        if (!isOk) {
//            errorView.setVisibility(View.VISIBLE);
//            errorView.setText(callResult.comment);
//        }
//
//        mPage.setActionCompleted(isOk);
//
//        mPage.getData().putString(Page.SIMPLE_DATA_KEY, "ddd");
//        mPage.notifyDataChanged();
    }

    @Override
    public void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage) {
        if (podInitActionType.isParent()) {
            for (PodInitActionType actionType : mapCheckBoxes.keySet()) {
                setCheckBox(actionType, isSuccess);
            }

            // special handling for init
            processOnFinishedActions(isSuccess, errorMessage);

        } else {
            setCheckBox(podInitActionType, isSuccess);
        }
    }


    private void processOnFinishedActions(boolean isOk, String errorMessage) {

        getActivity().runOnUiThread(() -> {

            progressBar.setVisibility(View.GONE);

            if (!isOk) {
                errorView.setVisibility(View.VISIBLE);
                errorView.setText(errorMessage);

                retryButton.setVisibility(View.VISIBLE);
            }

            mPage.setActionCompleted(isOk);

            mPage.getData().putString(Page.SIMPLE_DATA_KEY, UUID.randomUUID().toString());
            mPage.notifyDataChanged();

        });

    }


    public void setCheckBox(PodInitActionType podInitActionType, boolean isSuccess) {
        getActivity().runOnUiThread(() -> {
            mapCheckBoxes.get(podInitActionType).setChecked(isSuccess);
            mapCheckBoxes.get(podInitActionType).setTextColor(isSuccess ? Color.rgb(34, 135, 91) :
                    Color.rgb(168, 36, 15));
        });
    }


}
