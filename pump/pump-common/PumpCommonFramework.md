Pump Common Framework
======================

Intention of this classes is easier adding of new pump drivers, without duplicating too much
code.

Some of this classes were used by Medtronic driver, but a lot of them are new (intended for
newer drivers). I am working on Tandem driver at the moment (you can look it up in my
repository https://github.com/andyrozman/AndroidAPS/tree/andy_tandem as that code is
better sample, as Medtronic is).

Your main NEW_PUMP_PumpPlugin class should extend PumpPluginAbstract class. You will have to
implement some classes:

NEW_PUMP_PumpStatus (extends PumpStatus), this is for data storing (same data is already
there, you would need just to add pump specific data)

NEW_PUMP_PumpUtil (extends PumpUtil), this is utility class, for operations common to other
classes.

Your driver should implement PumpDriverConfigurationCapable, and then you should create
PumpDriverConfiguration class for your pump, which will provide configuration for
PumpBLESelector (common configuration selector for BLE devices) and PumpHistoryDataProvider
for dialog for history.

PumpConnectorInterface should be used for connection to low level driver for your pump,
it contains all methods that driver needs. At the moment it is not directly tied to
PumpPluginAbstract, but in future it will be. PumpConnectorInterface and
PumpConnectionManager should be used as middle-layer for pump communication.


LOT MORE OF DOCUMENTATION TO COME




Version History
===============

1.2 (20.2.2023) - added PumpDriverConfiguration and PumpConnectionManager main classes
1.1 (2022) - added few classes (BleConfiguration), for use with experimenta Ypso driver
1.0 (2017) - Initial version (used with Medtronic and Omnipod)


