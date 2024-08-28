# Only Working on "Dev" branch

# UI Changes
* Overview: Buttons, Alignment, Scaling
* Overview: Presentation of AutoSense removed "AS: "
* Funnel Animation for overview dialogs
* Dialog Insulin: Buttons, Alignment
* Dialog Carbs: Buttons, Alignment
* Dialog Wizard: Buttons, Alignment
* Dialog Bolus Progress: Buttons, Alignment
* Dialog Calibration: Alignment
* Dialog Care: Alignment
* Profile Helper: Buttons, Alignment, Scaling
* Profile TDD: Button, Alignment, Scaling
* Activity Survey: Buttons, Alignment, Scaling
* Autotune: Buttons, Spacing
* Danar: Buttons
* Diaconn G8: Buttons
* Equil: Buttons
* Medtronic: Buttons
* Omnipod Common: Buttons, Changes, Alignment
* Omnipod Dash: Buttons, Alignment, Centering
* Omnipod Eros: Buttons

## New Files
# Drawables added:
UI button and states for "Bolus Wizard" and "Activity Survey" since they use either >Button< or >ToggleButton< value.
UI background for label in "Bolus Wizard" dialog.

Location:
/ui/src/main/res/drawable/
/plugins/main/src/main/res/drawable

Added:
ui_bolwiz_title_bg.xml
ui.button_state.xml
ui.button_press.xml
ui.button_bg.xml

# Animations added:
Dialogs: Carbs, Insulin, TempTarget, Bolus Wizard

Location:
/ui/src/main/res/anim/

Added:
carbs_dialog_funnel_down.xml
carbs_dialog_funnel_up.xml
insulin_dialog_funnel_down.xml
insulin_dialog_funnel_up.xml
loop_dialog_funnel_down.xml
loop_dialog_funnel_up.xml
profile_viewer_funnel_down.xml
profile_viewer_funnel_up.xml
temp_dialog_funnel_down.xml
temp_dialog_funnel_up.xml
wiz_dialog_funnel_down.xml
wiz_dialog_funnel_up.xml

## String Changes
Location:
/core/ui/src/main/res/values/strings/strings.xml
String change: line 159
Changes "overview_insulin_label" to " " since i was unable to fully remove the label with >android:text=" "<

Location:
/ui/src/main/res/values/styles/styles.xml
Strings Added: line 12 > 40
"TempDialogFunnelAnimation"
"WizDialogFunnelAnimation"
"CarbsDialogFunnelAnimation"
"InsulinDialogFunnelAnimation"
"LoopDialogFunnelAnimation"
"ProfileViewerFunnelAnimation"



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


