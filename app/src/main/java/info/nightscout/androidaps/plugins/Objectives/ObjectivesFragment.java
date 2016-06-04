package info.nightscout.androidaps.plugins.Objectives;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.R;

public class ObjectivesFragment extends Fragment {
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    private OnFragmentInteractionListener mListener;

    class Objective {
        String objective;
        String gate;
        Date started;
        Integer durationInDays;
        Date accomplished;

        Objective(String objective, String gate, Date started, Integer durationInDays, Date accomplished) {
            this.objective = objective;
            this.gate = gate;
            this.started = started;
            this.durationInDays = durationInDays;
            this.accomplished = accomplished;
        }
    }

    private List<Objective> objectives;

    private void initializeData() {
        objectives = new ArrayList<>();
        objectives.add(new Objective("Setting up visualization and monitoring, and analyzing basals and ratios",
                "Verify that BG is available in Nightscout, and pump insulin data is being uploaded",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Starting on an open loop",
                "Run in Open Loop mode for a few days, and manually enact lots of temp basals",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Understanding your open loop, including its temp basal recommendations",
                "Based on that experience, decide what max basal should be, and set it on the pump",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Starting to close the loop with Low Glucose Suspend",
                "Run in closed loop with max IOB = 0 for a few days without too many LGS events",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Tuning the closed loop, raising max IOB above 0 and gradually lowering BG targets",
                "Run for a few days, and at least one night with no low BG alarms, before dropping BG",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Adjust basals and ratios if needed, and then enable auto-sens",
                "1 week successful daytime looping with regular carb entry",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Enabling additional features for daytime use, such as advanced meal assist",
                "",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ObjectiveViewHolder> {

        List<Objective> objectives;

        RecyclerViewAdapter(List<Objective> objectives) {
            this.objectives = objectives;
        }

        @Override
        public ObjectiveViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.objectives_item, viewGroup, false);
            ObjectiveViewHolder objectiveViewHolder = new ObjectiveViewHolder(v);
            return objectiveViewHolder;
        }

        @Override
        public void onBindViewHolder(ObjectiveViewHolder holder, int position) {
            holder.position.setText(String.valueOf(position+1));
            holder.objective.setText(objectives.get(position).objective);
            holder.gate.setText(objectives.get(position).gate);
            holder.started.setText(objectives.get(position).started.toString());
            holder.accomplished.setText(objectives.get(position).accomplished.toString());
        }

        @Override
        public int getItemCount() {
            return objectives.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class ObjectiveViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView position;
            TextView objective;
            TextView gate;
            TextView started;
            TextView accomplished;

            ObjectiveViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.objectives_cardview);
                position = (TextView) itemView.findViewById(R.id.objectives_position);
                objective = (TextView) itemView.findViewById(R.id.objectives_objective);
                gate = (TextView) itemView.findViewById(R.id.objectives_gate);
                started = (TextView) itemView.findViewById(R.id.objectives_started);
                accomplished = (TextView) itemView.findViewById(R.id.objectives_accomplished);
            }
        }
    }

    public ObjectivesFragment() {
        super();
        initializeData();
    }

    public static ObjectivesFragment newInstance() {
        ObjectivesFragment fragment = new ObjectivesFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.objectives_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.objectives_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(objectives);
        recyclerView.setAdapter(adapter);

        return view;
    }

    /*
        // TODO: Rename method, update argument and hook method into UI event
        public void onButtonPressed(Uri uri) {
            if (mListener != null) {
                mListener.onFragmentInteraction(uri);
            }
        }
    */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(String param);
    }
}
