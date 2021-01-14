package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.Collections;
import java.util.List;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class Objective2 extends Objective {


    public Objective2(HasAndroidInjector injector) {
        super(injector, "exam", R.string.objectives_exam_objective, R.string.objectives_exam_gate);
        for (Task task : tasks) {
            if (!task.isCompleted()) setAccomplishedOn(0);
        }
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new ExamTask(R.string.prerequisites_label, R.string.prerequisites_what, "prerequisites")
                .option(new Option(R.string.prerequisites_nightscout, true))
                .option(new Option(R.string.prerequisites_computer, true))
                .option(new Option(R.string.prerequisites_pump, true))
                .option(new Option(R.string.prerequisites_beanandroiddeveloper, false))
                .hint(new Hint(R.string.prerequisites_hint1))
        );
        tasks.add(new ExamTask(R.string.prerequisites2_label, R.string.prerequisites2_what, "prerequisites2")
                .option(new Option(R.string.prerequisites2_profile, true))
                .option(new Option(R.string.prerequisites2_device, true))
                .option(new Option(R.string.prerequisites2_internet, false))
                .option(new Option(R.string.prerequisites2_supportedcgm, true))
                .hint(new Hint(R.string.prerequisites2_hint1))
        );
        tasks.add(new ExamTask(R.string.basaltest_label, R.string.basaltest_when,"basaltest")
                .option(new Option(R.string.basaltest_fixed, false))
                .option(new Option(R.string.basaltest_havingregularhighlow, true))
                .option(new Option(R.string.basaltest_weekly, false))
                .option(new Option(R.string.basaltest_beforeloop, true))
                .hint(new Hint(R.string.basaltest_hint1))
        );
        tasks.add(new ExamTask(R.string.dia_label_exam, R.string.dia_whatmeansdia,"dia")
                .option(new Option(R.string.dia_profile, true))
                .option(new Option(R.string.dia_minimumis5h, true))
                .option(new Option(R.string.dia_meaningisequaltodiapump, false))
                .option(new Option(R.string.dia_valuemustbedetermined, true))
                .hint(new Hint(R.string.dia_hint1))
        );
        tasks.add(new ExamTask(R.string.isf_label_exam, R.string.blank,"isf")
                .option(new Option(R.string.isf_decreasingvalue, true))
                .option(new Option(R.string.isf_preferences, false))
                .option(new Option(R.string.isf_increasingvalue, false))
                .option(new Option(R.string.isf_noeffect, false))
                .hint(new Hint(R.string.isf_hint1))
                .hint(new Hint(R.string.isf_hint2))
        );
        tasks.add(new ExamTask(R.string.ic_label_exam, R.string.blank,"ic")
                .option(new Option(R.string.ic_increasingvalue, true))
                .option(new Option(R.string.ic_decreasingvalue, false))
                .option(new Option(R.string.ic_multiple, true))
                .option(new Option(R.string.ic_isf, false))
                .hint(new Hint(R.string.ic_hint1))
        );
        tasks.add(new ExamTask(R.string.hypott_label, R.string.hypott_whenhypott,"hypott")
                .option(new Option(R.string.hypott_preventoversmb, true))
                .option(new Option(R.string.hypott_exercise, false))
                .option(new Option(R.string.hypott_wrongbasal, false))
                .option(new Option(R.string.hypott_0basal, false))
                .hint(new Hint(R.string.hypott_hint1))
        );
        tasks.add(new ExamTask(R.string.profileswitch_label, R.string.profileswitch_pctwillchange,"profileswitch")
                .option(new Option(R.string.profileswitch_basallower, true))
                .option(new Option(R.string.profileswitch_isfhigher, true))
                .option(new Option(R.string.profileswitch_iclower, false))
                .option(new Option(R.string.profileswitch_unchanged, false))
                .hint(new Hint(R.string.profileswitch_hint1))
        );
        tasks.add(new ExamTask(R.string.profileswitch2_label, R.string.profileswitch2_pctwillchange,"profileswitch2")
                .option(new Option(R.string.profileswitch2_bghigher, false))
                .option(new Option(R.string.profileswitch2_basalhigher, true))
                .option(new Option(R.string.profileswitch2_bgunchanged, true))
                .option(new Option(R.string.profileswitch2_isfhigher, false))
                .hint(new Hint(R.string.profileswitch_hint1))
        );
        tasks.add(new ExamTask(R.string.profileswitchtime_label, R.string.profileswitchtime_iwant,"profileswitchtime")
                .option(new Option(R.string.profileswitchtime_2, false))
                .option(new Option(R.string.profileswitchtime__2, true))
                .option(new Option(R.string.profileswitchtime_tt, false))
                .option(new Option(R.string.profileswitchtime_100, false))
                .hint(new Hint(R.string.profileswitchtime_hint1))
        );
        tasks.add(new ExamTask(R.string.profileswitch4_label, R.string.blank,"profileswitch4")
                .option(new Option(R.string.profileswitch4_rates, true))
                .option(new Option(R.string.profileswitch4_internet, true))
                .option(new Option(R.string.profileswitch4_sufficient, false))
                .option(new Option(R.string.profileswitch4_multi, true))
                .hint(new Hint(R.string.profileswitch_hint1))
        );
        tasks.add(new ExamTask(R.string.exerciseprofile_label, R.string.exerciseprofile_whattodo,"exercise")
                .option(new Option(R.string.exerciseprofile_switchprofileabove100, false))
                .option(new Option(R.string.exerciseprofile_switchprofilebelow100, true))
                .option(new Option(R.string.exerciseprofile_suspendloop, false))
                .option(new Option(R.string.exerciseprofile_leaveat100, false))
                .hint(new Hint(R.string.exerciseprofile_hint1))
        );
        tasks.add(new ExamTask(R.string.exercise_label, R.string.exercise_whattodo,"exercise2")
                .option(new Option(R.string.exercise_settt, true))
                .option(new Option(R.string.exercise_setfinished, false))
                .option(new Option(R.string.exercise_setunchanged, false))
                .option(new Option(R.string.exercise_15g, false))
                .hint(new Hint(R.string.exercise_hint1))
        );
        tasks.add(new ExamTask(R.string.noisycgm_label, R.string.noisycgm_whattodo,"noisycgm")
                .option(new Option(R.string.noisycgm_nothing, false))
                .option(new Option(R.string.noisycgm_pause, true))
                .option(new Option(R.string.noisycgm_replacesensor, true))
                .option(new Option(R.string.noisycgm_checksmoothing, true))
                .hint(new Hint(R.string.noisycgm_hint1))
        );
        tasks.add(new ExamTask(R.string.pumpdisconnect_label, R.string.blank,"pumpdisconnect")
                .option(new Option(R.string.pumpdisconnect_unnecessary, false))
                .option(new Option(R.string.pumpdisconnect_missinginsulin, true))
                .option(new Option(R.string.pumpdisconnect_notstop, false))
                .option(new Option(R.string.pumpdisconnect_openloop, false))
                .hint(new Hint(R.string.pumpdisconnect_hint1))
        );
        tasks.add(new ExamTask(R.string.insulin_label, R.string.insulin_ultrarapid,"insulin")
                .option(new Option(R.string.insulin_novorapid, false))
                .option(new Option(R.string.insulin_humalog, false))
                .option(new Option(R.string.insulin_actrapid, false))
                .option(new Option(R.string.insulin_fiasp, true))
                .hint(new Hint(R.string.insulin_hint1))
        );
        tasks.add(new ExamTask(R.string.sensitivity_label, R.string.blank,"sensitivity")
                .option(new Option(R.string.sensitivity_adjust, true))
                .option(new Option(R.string.sensitivity_edit, false))
                .option(new Option(R.string.sensitivity_cannula, true))
                .option(new Option(R.string.sensitivity_time, true))
                .hint(new Hint(R.string.sensitivity_hint1))
        );
        tasks.add(new ExamTask(R.string.objectives_label, R.string.objectives_howtosave,"objectives")
                .option(new Option(R.string.objectives_notesettings, false))
                .option(new Option(R.string.objectives_afterobjective, true))
                .option(new Option(R.string.objectives_afterchange, true))
                .option(new Option(R.string.objectives_afterinitialsetup, true))
                .hint(new Hint(R.string.objectives_hint1))
                .hint(new Hint(R.string.objectives_hint2))
        );
        tasks.add(new ExamTask(R.string.objectives2_label, R.string.objectives_howtosave,"objectives2")
                .option(new Option(R.string.objectives2_maintenance, true))
                .option(new Option(R.string.objectives2_internalstorage, true))
                .option(new Option(R.string.objectives2_cloud, true))
                .option(new Option(R.string.objectives2_easyrestore, false))
                .hint(new Hint(R.string.objectives_hint1))
                .hint(new Hint(R.string.objectives_hint2))
        );
        tasks.add(new ExamTask(R.string.update_label, R.string.blank,"update")
                .option(new Option(R.string.update_git, true))
                .option(new Option(R.string.update_askfriend, false))
                .option(new Option(R.string.update_keys, true))
                .option(new Option(R.string.update_asap, true))
                .hint(new Hint(R.string.update_hint1))
        );
        tasks.add(new ExamTask(R.string.troubleshooting_label, R.string.troubleshooting_wheretoask,"troubleshooting")
                .option(new Option(R.string.troubleshooting_fb, true))
                .option(new Option(R.string.troubleshooting_wiki, true))
                .option(new Option(R.string.troubleshooting_gitter, true))
                .option(new Option(R.string.troubleshooting_yourendo, false))
                .hint(new Hint(R.string.troubleshooting_hint1))
                .hint(new Hint(R.string.troubleshooting_hint2))
                .hint(new Hint(R.string.troubleshooting_hint3))
        );
        tasks.add(new ExamTask(R.string.wrongcarbs_label, R.string.wrongcarbs_whattodo,"wrongcarbs")
                .option(new Option(R.string.wrongcarbs_addinsulin, false))
                .option(new Option(R.string.wrongcarbs_treatmentstab, true))
                .option(new Option(R.string.wrongcarbs_donothing, false))
                .option(new Option(R.string.wrongcarbs_bolus, false))
        );
        tasks.add(new ExamTask(R.string.wronginsulin_label, R.string.wronginsulin_whattodo,"wronginsulin")
                .option(new Option(R.string.wronginsulin_careportal, false))
                .option(new Option(R.string.wronginsulin_compare, true))
                .option(new Option(R.string.wronginsulin_prime, true))
                .option(new Option(R.string.wrongcarbs_donothing, false))
        );
        tasks.add(new ExamTask(R.string.iob_label, R.string.blank,"iob")
                .option(new Option(R.string.iob_value, true))
                .option(new Option(R.string.iob_hightemp, false))
                .option(new Option(R.string.iob_negiob, true))
                .option(new Option(R.string.iob_posiob, true))
        );
        tasks.add(new ExamTask(R.string.breadgrams_label, R.string.blank,"breadgrams")
                .option(new Option(R.string.breadgrams_grams, true))
                .option(new Option(R.string.breadgrams_exchange, false))
                .option(new Option(R.string.breadgrams_decay, true))
                .option(new Option(R.string.breadgrams_calc, true))
                .hint(new Hint(R.string.breadgrams_hint1))
        );
        tasks.add(new ExamTask(R.string.extendedcarbs_label, R.string.extendedcarbs_handling,"extendedcarbs")
                .option(new Option(R.string.extendedcarbs_future, true))
                .option(new Option(R.string.extendedcarbs_free, false))
                .option(new Option(R.string.extendedcarbs_fat, true))
                .option(new Option(R.string.extendedcarbs_rescue, false))
                .hint(new Hint(R.string.extendedcarbs_hint1))
        );
        tasks.add(new ExamTask(R.string.nsclient_label, R.string.nsclient_howcanyou,"nsclient")
                .option(new Option(R.string.nsclient_nightscout, true))
                .option(new Option(R.string.nsclient_dexcomfollow, true))
                .option(new Option(R.string.nsclient_data, true))
                .option(new Option(R.string.nsclient_fullcontrol, false))
                .hint(new Hint(R.string.nsclient_hint1))
        );
        tasks.add(new ExamTask(R.string.other_medication_label, R.string.other_medication_text,"otherMedicationWarning")
                .option(new Option(R.string.yes, true))
                .option(new Option(R.string.no, false))
        );

        for (Task task : tasks)
            Collections.shuffle(((ExamTask)task).options);
    }

}
