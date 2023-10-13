# AndroidAPS_For_Equil


This project primarily integrates a Bluetooth driver named Equil Patch Pump, enabling it to operate with AndroidAPS. The driver is based on Equil Patch Pump firmware version 5.3. I've heard that the Equil Patch Pump has been released in some markets, but I am unsure about the differences in communication protocol compared to v5.3, and I don't have devices with other firmware versions for verification, which means this code is only applicable to Equil Patch Pump v5.3. If you have any questions regarding the compatibility of this brand's products with AndroidAPS, you can consult the dealer for confirmation.

Please understand that this project:
- Is highly experimental
- Is not approved for therapy

Functions completed by the Bluetooth driver include:
- Binding and unbinding of AndroidAPS with Equil Patch Pump
- Initialization settings for Equil Patch Pump, including time setting, basal rate setting, and pump reminder mode setting
- Installation and removal of infusion apparatus, including reset of the driving screw, filling, and air expulsion from the insulin reservoir
- Insulin Delivery Commands: Bolus (excluding extended bolus), basal rate setting, temporary basal rate setting, pump pause/resume
- Pump information synchronization: device firmware information, battery level, insulin Reservoir, historical events (including delivery history, alarm errors, etc.)





* Check the wiki: https://androidaps.readthedocs.io
*  Everyone who’s been looping with AndroidAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.androidaps.org/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://androidaps.readthedocs.io/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)


<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2
