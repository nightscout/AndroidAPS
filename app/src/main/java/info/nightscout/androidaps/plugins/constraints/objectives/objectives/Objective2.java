package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin;
import info.nightscout.androidaps.utils.SP;

public class Objective2 extends Objective {


    public Objective2() {
        super("exam", R.string.objectives_exam_objective, R.string.objectives_exam_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new ExamTask(R.string.meaningofdia, R.string.whatmeansdia,"dia")
                .option(new Option(R.string.minimumis3h, false))
                .option(new Option(R.string.minimumis5h, true))
                .option(new Option(R.string.meaningisequaltodiapump, false))
                .option(new Option(R.string.valuemustbedetermined, true))
                .hint(new Hint(R.string.diahint1))
        );
     }
}
