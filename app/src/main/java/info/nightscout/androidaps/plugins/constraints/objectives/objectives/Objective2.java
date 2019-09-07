package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.R;

public class Objective2 extends Objective {


    public Objective2() {
        super("exam", R.string.objectives_exam_objective, R.string.objectives_exam_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new ExamTask(R.string.dia_meaningofdia, R.string.dia_whatmeansdia,"dia")
                .option(new Option(R.string.dia_minimumis3h, false))
                .option(new Option(R.string.dia_minimumis5h, true))
                .option(new Option(R.string.dia_meaningisequaltodiapump, false))
                .option(new Option(R.string.dia_valuemustbedetermined, true))
                .hint(new Hint(R.string.dia_hint1))
        );
        tasks.add(new ExamTask(R.string.hypott, R.string.hypott_whenhypott,"hypott")
                .option(new Option(R.string.hypott_goinglow, false))
                .option(new Option(R.string.hypott_havinglow, false))
                .option(new Option(R.string.hypott_preventoversmb, true))
                .hint(new Hint(R.string.hypott_hint1))
        );
        tasks.add(new ExamTask(R.string.offlineprofile, R.string.offlineprofile_whatprofile,"offlineprofile")
                .option(new Option(R.string.localprofile, true))
                .option(new Option(R.string.nsprofile, false))
                .option(new Option(R.string.offlineprofile_nsprofile, true))
                .hint(new Hint(R.string.offlineprofile_hint1))
        );
        tasks.add(new ExamTask(R.string.pumpdisconnect, R.string.pumpdisconnect_whattodo,"pumpdisconnect")
                .option(new Option(R.string.pumpdisconnect_letknow, true))
                .option(new Option(R.string.pumpdisconnect_dontchnage, false))
                .hint(new Hint(R.string.pumpdisconnect_hint1))
        );
        tasks.add(new ExamTask(R.string.objectives, R.string.objectives_howtosave,"objectives")
                .option(new Option(R.string.objectives_writetopaper, false))
                .option(new Option(R.string.objectives_exportsettings, true))
                .option(new Option(R.string.objectives_storeelsewhere, true))
                .option(new Option(R.string.objectives_doexportonstart, false))
                .option(new Option(R.string.objectives_doexportafterchange, true))
                .option(new Option(R.string.objectives_doexportafterfirtssettings, true))
                .hint(new Hint(R.string.objectives_hint1))
        );
        tasks.add(new ExamTask(R.string.noisycgm, R.string.noisycgm_whattodo,"noisycgm")
                .option(new Option(R.string.nothing, false))
                .option(new Option(R.string.disconnectpumpfor1h, false))
                .option(new Option(R.string.noisycgm_pause, true))
                .option(new Option(R.string.noisycgm_replacesensor, true))
                .option(new Option(R.string.noisycgm_turnoffcgmreceiver, false))
                .option(new Option(R.string.noisycgm_checksmoothing, true))
                .hint(new Hint(R.string.noisycgm_hint1))
        );
        tasks.add(new ExamTask(R.string.exercise, R.string.exercise_whattodo,"exercise")
                .option(new Option(R.string.nothing, false))
                .option(new Option(R.string.exercise_setactivitytt, true))
                .option(new Option(R.string.exercise_switchprofilebelow100, true))
                .option(new Option(R.string.exercise_switchprofileabove100, false))
                .option(new Option(R.string.exercise_stoploop, false))
                .option(new Option(R.string.exercise_doitbeforestart, true))
                .option(new Option(R.string.exercise_doitafterstart, false))
                .hint(new Hint(R.string.exercise_hint1))
        );
       tasks.add(new ExamTask(R.string.suspendloop, R.string.suspendloop_doigetinsulin,"suspendloop")
                .option(new Option(R.string.suspendloop_yes, true))
                .option(new Option(R.string.suspendloop_no, false))
                 .hint(new Hint(R.string.exercise_hint1))
        );
       tasks.add(new ExamTask(R.string.basaltest, R.string.basaltest_when,"basaltest")
                .option(new Option(R.string.basaltest_beforeloop, true))
                .option(new Option(R.string.basaltest_havingregularhypo, true))
                .option(new Option(R.string.basaltest_havingregularhyper, true))
                 .hint(new Hint(R.string.basaltest_hint1))
        );
       tasks.add(new ExamTask(R.string.prerequisites, R.string.prerequisites_what, "prerequisites")
                .option(new Option(R.string.prerequisites_determinedcorrectprofile, true))
                .option(new Option(R.string.prerequisites_computer, true))
                .option(new Option(R.string.prerequisites_phone, true))
                .option(new Option(R.string.prerequisites_car, false))
                .option(new Option(R.string.prerequisites_nightscout, true))
                .option(new Option(R.string.prerequisites_tidepoolaccount, false))
                .option(new Option(R.string.prerequisites_googleaccount, false))
                .option(new Option(R.string.prerequisites_githubaccount, false))
                .option(new Option(R.string.prerequisites_beanandroiddeveloper, false))
                .option(new Option(R.string.prerequisites_own670g, false))
                .option(new Option(R.string.prerequisites_smartwatch, false))
                .option(new Option(R.string.prerequisites_supportedcgm, true))
                .hint(new Hint(R.string.prerequisites_hint1))
        );
        tasks.add(new ExamTask(R.string.update_update, R.string.whatistrue,"update")
                .option(new Option(R.string.update_git, true))
                .option(new Option(R.string.update_asap, true))
                .option(new Option(R.string.update_keys, true))
                .option(new Option(R.string.update_neverupdate, false))
                .option(new Option(R.string.update_askfriend, false))
                .hint(new Hint(R.string.update_hint1))
        );
        tasks.add(new ExamTask(R.string.troubleshooting, R.string.troubleshooting_wheretoask,"troubleshooting")
                .option(new Option(R.string.troubleshooting_fb, true))
                .option(new Option(R.string.troubleshooting_wiki, true))
                .option(new Option(R.string.troubleshooting_gitter, true))
                .option(new Option(R.string.troubleshooting_googlesupport, false))
                .option(new Option(R.string.troubleshooting_yourendo, false))
                .hint(new Hint(R.string.troubleshooting_hint1))
                .hint(new Hint(R.string.troubleshooting_hint2))
                .hint(new Hint(R.string.troubleshooting_hint3))
        );

        for (Task task : tasks)
            Collections.shuffle(((ExamTask)task).options);
    }

    @Override
    public boolean isRevertable() {
        return true;
    }

}
