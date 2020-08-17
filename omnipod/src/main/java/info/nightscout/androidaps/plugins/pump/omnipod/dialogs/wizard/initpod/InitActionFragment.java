package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
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

import androidx.fragment.app.FragmentActivity;

import com.atech.android.library.wizardpager.util.WizardPagesUtil;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;

/**
 * Created by andy on 12/11/2019
 */
public class InitActionFragment extends DaggerFragment implements PodInitReceiver {
    protected static final String ARG_KEY = "key";
    protected static final String ARG_POD_INIT_ACTION_TYPE = "podInitActionType";

    private static boolean isFirstView;

    protected PageFragmentCallbacks mCallbacks;
    protected String mKey;
    protected InitActionPage mPage;

    protected ProgressBar progressBar;
    protected TextView errorView;
    protected Button retryButton;

    protected PodInitActionType podInitActionType;
    protected List<PodInitActionType> children;
    protected Map<PodInitActionType, CheckBox> mapCheckBoxes;

    protected PumpEnactResult callResult;

    @Inject HasAndroidInjector injector;

    public static InitActionFragment create(String key, PodInitActionType podInitActionType) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putSerializable(ARG_POD_INIT_ACTION_TYPE, podInitActionType);

        InitActionFragment fragment = new InitActionFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            isFirstView = true;
        }

        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Bundle args = getArguments();
        mKey = args.getString(ARG_KEY);
        podInitActionType = (PodInitActionType) args.getSerializable(ARG_POD_INIT_ACTION_TYPE);
        mPage = (InitActionPage) mCallbacks.onGetPage(mKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

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

            new InitPodTask(injector, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });

        return rootView;
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

    @Override
    public void onResume() {
        super.onResume();
        if (isFirstView) {
            isFirstView = false;
            new InitPodTask(injector, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void actionOnReceiveResponse(String result) {
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
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
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
    }

    public void setCheckBox(PodInitActionType podInitActionType, boolean isSuccess) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                mapCheckBoxes.get(podInitActionType).setChecked(isSuccess);
                mapCheckBoxes.get(podInitActionType).setTextColor(isSuccess ? Color.rgb(34, 135, 91) :
                        Color.rgb(168, 36, 15));
            });
        }
    }

}
