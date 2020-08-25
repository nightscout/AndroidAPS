/*
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model;

import android.content.Context;

import androidx.fragment.app.Fragment;

import com.atech.android.library.wizardpager.model.DisplayTextPage;
import com.tech.freak.wizardpager.model.AbstractWizardModel;
import com.tech.freak.wizardpager.model.PageList;

import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.PodInfoFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod.RemovePodActionPage;

/**
 * Created by andy on 12/11/2019
 */
public class RemovePodWizardModel extends AbstractWizardModel {

    public RemovePodWizardModel(Context context) {
        super(context);
    }

    @Override
    protected PageList onNewRootPageList() {
        return new PageList(
                new DisplayTextPage(this,
                        R.string.omnipod_remove_pod_wizard_step1_title,
                        R.string.omnipod_remove_pod_wizard_step1_desc,
                        R.style.WizardPagePodContent).setRequired(true).setCancelReason("None"),

                new RemovePodActionPage(this,
                        R.string.omnipod_remove_pod_wizard_step2_title,
                        PodInitActionType.DeactivatePodWizardStep
                ).setRequired(true).setCancelReason("Cancel")

        );
    }

    public Fragment getReviewFragment() {
        return PodInfoFragment.create("removePodInfoFragment", false);
    }

}
