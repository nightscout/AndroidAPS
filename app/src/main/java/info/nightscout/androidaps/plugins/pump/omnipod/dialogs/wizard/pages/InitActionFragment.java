package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;

/**
 * Created by TechFreak on 04/09/2014.
 */
public class InitActionFragment extends Fragment implements PodInitReceiver {
    private static final String ARG_KEY = "key";

    private PageFragmentCallbacks mCallbacks;
    private String mKey;
    private InitActionPage mPage;

    private ProgressBar progressBar;
    private TextView errorView;

    private PodInitActionType podInitActionType;
    //private List<PodInitActionType> children;
    private Map<PodInitActionType, CheckBox> mapCheckBoxes;
    private InitActionFragment instance;

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

        List<PodInitActionType> children = podInitActionType.getChildren();
        mapCheckBoxes = new HashMap<>();

        for (PodInitActionType child : children) {

            CheckBox checkBox1 = new CheckBox(getContext());
            checkBox1.setText(child.getResourceId());
            checkBox1.setClickable(false);
            checkBox1.setTextAppearance(R.style.WizardPagePodListItem);
            checkBox1.setHeight(140);
            checkBox1.setTextSize(16);
            checkBox1.setTextColor(headerView.getTextColors().getDefaultColor());

            linearLayout.addView(checkBox1);

            mapCheckBoxes.put(child, checkBox1);
        }

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

//    @Override
//    public void onViewCreated(View view, Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//
//    }

//    @Override
//    public void setMenuVisibility(boolean menuVisible) {
//        super.setMenuVisibility(menuVisible);
//
//        // In a future update to the support library, this should override setUserVisibleHint
//        // instead of setMenuVisibility.
//
//    }

    public PodInitActionType getPodInitActionType() {
        return podInitActionType;
    }


    public void setPodInitActionType(PodInitActionType podInitActionType) {
        this.podInitActionType = podInitActionType;
    }


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        System.out.println("ACTION: setUserVisibleHint="+ isVisibleToUser);
        if (isVisibleToUser) {
            System.out.println("ACTION: Visible");

            new AsyncTask<Void, Void, String>() {

                PumpEnactResult callResult;

                protected void onPreExecute() {
                    progressBar.setVisibility(View.VISIBLE);
                }

                @Override
                protected String doInBackground(Void... params) {
                    if (podInitActionType == PodInitActionType.PairAndPrimeWizardStep) {
                        this.callResult = AapsOmnipodManager.getInstance().initPod(
                                podInitActionType,
                                instance,
                                null
                        );
                    } else if (podInitActionType == PodInitActionType.FillCannulaSetBasalProfileWizardStep) {
                        this.callResult = AapsOmnipodManager.getInstance().initPod(
                                podInitActionType,
                                instance,
                                ProfileFunctions.getInstance().getProfile()
                        );
                    } else if (podInitActionType == PodInitActionType.DeactivatePodWizardStep) {
                        this.callResult = AapsOmnipodManager.getInstance().deactivatePod(instance);
                    }

                    return "OK";
                }

                @Override
                protected void onPostExecute(String result) {
                    super.onPostExecute(result);

                    System.out.println("ACTION: onPostExecute: " + result);

                    boolean isOk = callResult.success;

                    progressBar.setVisibility(View.GONE);

                    if (!isOk) {
                        errorView.setVisibility(View.VISIBLE);
                        errorView.setText(callResult.comment);
                    }

                    mPage.setActionCompleted(isOk);

                    mPage.getData().putString(Page.SIMPLE_DATA_KEY, "ddd");
                    mPage.notifyDataChanged();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            System.out.println("ACTION: Not visible");
        }
    }

    @Override
    public void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage) {
        if (podInitActionType.isParent()) {
            for (PodInitActionType actionType : mapCheckBoxes.keySet()) {
                setCheckBox(actionType, isSuccess);
            }
        } else {
            setCheckBox(podInitActionType, isSuccess);
        }
    }


    public void setCheckBox(PodInitActionType podInitActionType, boolean isSuccess) {
        getActivity().runOnUiThread(() -> {
            mapCheckBoxes.get(podInitActionType).setChecked(true);
            mapCheckBoxes.get(podInitActionType).setTextColor(isSuccess ? Color.rgb(34, 135, 91) :
                    Color.rgb(168, 36, 15));
        });
    }




}
