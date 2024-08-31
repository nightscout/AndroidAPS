# Only Working on "Dev" branch

# TL:DR
* Scared to cut my fingers, buttons now have round edges
* Dialog closing/opening for Overview items have a sexy funnel animation
* Overall alignment and centering for dialog objects/items

# UI Changes
* Overview: Buttons, Alignment, Scaling
* Overview: Presentation of AutoSense removed "AS: "
* Automation Dialog: Buttons, Alignment, Centering
* Activity Survey: Buttons, Alignment, Scaling
* Autotune: Buttons, Spacing
* Dialog Insulin: Buttons, Alignment
* Dialog Carbs: Buttons, Alignment
* Dialog Wizard: Buttons, Alignment
* Dialog Bolus Progress: Buttons, Alignment
* Dialog Calibration: Alignment
* Dialog Care: Alignment
* History Browser: Buttons
* Profile Helper: Buttons, Alignment, Scaling
* Profile TDD: Button, Alignment, Scaling
* Funnel Animation for overview dialogs
* Danar: Buttons
* Diaconn G8: Buttons
* Equil: Buttons
* Medtronic: Buttons
* Omnipod Common: Buttons, Changes, Alignment
* Omnipod Dash: Buttons, Alignment, Centering
* Omnipod Eros: Buttons

# New Files
## Drawables added:
UI button and states for "Bolus Wizard" and "Activity Survey" <br />
and "Automation Dialog" <br />
since they use either >Button< or >ToggleButton< value. <br />
UI background for label in "Bolus Wizard" dialog.

Location: <br />
/ui/src/main/res/drawable/ <br />
/plugins/main/src/main/res/drawable/  <br />
/plugins/automation/src/main/res/drawable/

Added:
ui_bolwiz_title_bg.xml <br />
ui.button_state.xml <br />
ui.button_press.xml <br />
ui.button_bg.xml <br />

## Animations added:
Overview: Carbs, Insulin, Loop Dialog, Profile Viewer, TempTarget, Bolus Wizard,

Location: <br />
/ui/src/main/res/anim/

Added: <br />
carbs_dialog_funnel_down.xml <br />
carbs_dialog_funnel_up.xml <br />
insulin_dialog_funnel_down.xml <br />
insulin_dialog_funnel_up.xml <br />
loop_dialog_funnel_down.xml <br />
loop_dialog_funnel_up.xml <br />
profile_viewer_funnel_down.xml <br />
profile_viewer_funnel_up.xml <br />
temp_dialog_funnel_down.xml <br />
temp_dialog_funnel_up.xml <br />
wiz_dialog_funnel_down.xml <br />
wiz_dialog_funnel_up.xml <br />

## String Changes
Location:
/core/ui/src/main/res/values/strings/strings.xml <br />
String change: line 159 <br />
Changes "overview_insulin_label" to " " since i was unable to fully remove the label with >android:text=" "<  <br />

Location: <br />
/ui/src/main/res/values/styles/styles.xml <br />
Strings Added: line 12 > 40 <br />
"TempDialogFunnelAnimation" <br />
"WizDialogFunnelAnimation" <br />
"CarbsDialogFunnelAnimation" <br />
"InsulinDialogFunnelAnimation" <br />
"LoopDialogFunnelAnimation" <br />
"ProfileViewerFunnelAnimation" <br />

# Preview (Outdated adjustments have been made)

<img src="https://github.com/user-attachments/assets/d88c8fed-e1da-4f0e-b6ce-a6d9d0afef0c" height="389" width="366"> <br />

<img src="https://github.com/user-attachments/assets/bcc63c33-337f-49c4-82b5-7b3bf48660ad" height="250" width="366"> <br />

<img src="https://github.com/user-attachments/assets/e984435b-b9ad-4077-b845-75a0eefb5d9d" height="388" width="366"> <br />

<img src="https://github.com/user-attachments/assets/19e5b78a-1b8b-4e52-b938-86d04acb37f3" height="409" width="366"> <br />

<img src="https://github.com/user-attachments/assets/7c7ba80f-9244-4031-b538-df272ecc15a3" height="326" width="366"> <br />


https://github.com/user-attachments/assets/7f97890f-2b5f-4834-97a8-1b9a2aee3481




# AAPS
* Check the wiki: https://wiki.aaps.app
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2
