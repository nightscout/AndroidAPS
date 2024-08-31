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
* Dialog: DateTime Alignment
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
UI button and states for "Bolus Wizard", "Activity Survey", <br />
"Automation Dialog" and "History Browser" <br />
since they use either >Button< or >ToggleButton< value. <br />
UI background for label in "Bolus Wizard" dialog.

Location: <br />
/ui/src/main/res/drawable/ <br />
/plugins/main/src/main/res/drawable/  <br />
/plugins/automation/src/main/res/drawable/  <br />
/core/ui/src/main/res/drawable/

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

# Preview

<img src="https://github.com/user-attachments/assets/190357e9-d5ae-4afa-ae91-60adc3a92e80" height="258" width="367">
<img src="https://github.com/user-attachments/assets/9045e81f-6fc9-4770-bc45-c4ac33ac40a2" height="343" width="367"> <br />

<img src="https://github.com/user-attachments/assets/2e697a53-b82f-4474-8b89-30a47447db94" height="348" width="367">
<img src="https://github.com/user-attachments/assets/c21bb355-8f1a-45d7-afca-673e1121b811" height="392" width="367"> <br />

<img src="https://github.com/user-attachments/assets/0640ec46-d626-4df5-b58c-fb75f52b9060" height="251" width="367">
<img src="https://github.com/user-attachments/assets/9c72c6c0-1be4-4c12-acd2-b4d15c825d33" height="306" width="367"> <br />







https://github.com/user-attachments/assets/aabb73c8-a525-4431-8d5a-f4da1386a31b







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
