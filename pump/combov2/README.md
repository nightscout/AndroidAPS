# Overview over combov2's and ComboCtl's architecture

ComboCtl is the core driver. It uses Kotlin Multiplatform and is written in a platform agnostic
way. The code is located in `comboctl/`, and is also available in [its own separate repository]
(https://github.com/dv1/ComboCtl). That separate repository is kept in sync with the ComboCtl
copy in AndroidAPS as much as possible, with some notable changes (see below). "combov2" is the
name of the AndroidAPS driver. In short: combov2 = ComboCtl + extra AndroidAPS integration code.

## Directory structure

The directory structure of the local ComboCtl itself is:

* `comboctl/src/commonMain/` : The platform agnostic portion of ComboCtl. The vast majority of
  ComboCtl's logic is contained there.
* `comboctl/src/androidMain/` : The Android specific code. This in particular contains
  implementations of the Bluetooth interfaces that are defined in `commonMain/`.
* `comboctl/src/jvmTest/` : Unit tests. This subdirectory is called `jvmTest` because in the
  ComboCtl repository, there is also a `jvmMain/` subdirectory, and the unit tests are run
  with the JVM.

The AndroidAPS specific portion of the driver is located in `src/`. This connects ComboCtl with
AndroidAPS. In particular, this is where the `ComboV2Plugin` class is located. That's the main
entrypoint for the combov2 driver plugin.

## Basic description of how ComboCtl communicates with the pump

ComboCtl uses Kotlin coroutines. It uses [the Default dispatcher](https://kotlinlang.
org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-default.html),
with [a limitedParallelism](https://kotlinlang.org/api/kotlinx.
coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html)
constraint to prevent actual parallelism, that is, to not let coroutine jobs run on multiple
threads concurrently. Coroutines are used in ComboCtl to greatly simplify the communication steps,
which normally require a number of state machines to be implemented manually. Stackless coroutines
like Kotlin's essentially are automatically generated state machines under the hood, and this is
what they are used for here. Enabling parallelism is not part of such a state machine. Furthermore,
communication with the Combo does not benefit from parallelism.

The communication code in ComboCtl is split in higher level operations (in its `Pump` class) and
lower level ones (in its `PumpIO` class). `Pump` instantiates `PumpIO` internally, and focuses on
implementing functionality like reading basal profiles, setting TBRs etc. `PumpIO` implements the
building blocks for these higher level operations. In particular, `PumpIO` has an internal
coroutine scope that is used for sending data to the Combo and for running a "heartbeat" loop.
That "heartbeat" is a message that needs to be regularly sent to the Combo (unless other data is
sent to the Combo in time, like a command to press a button). If nothing is sent to a Combo for
some time, it will eventually disconnect. For this reason, that heartbeat loop is necessary.

PumpIO also contains the code for performing the pump pairing.

Going further down a level, `TransportLayer` implements the IO code to generate packets for the
Combo and parse packets coming from the Combo. This includes code for authenticating outgoing
packets and for checking incoming ones. `TransportLayer` also contains the `IO` subclass, which
actually transfers packets to and receives data from the Combo.

One important detail to keep in mind about the `IO` class is that it enforces a packet send
interval of 200 ms. That is: The time between packet transmission is never shorter than 200 ms
(it is OK to be longer). The interval is important, because the Combo has a ring buffer for the
packet it receives, and transmitting packets to the Combo too quickly causes an overflow and a
subsequent error in the Combo, which then terminates the connection.

The Combo can run in three modes. The first one is the "service" mode, which is only briefly
used for setting up the connection. Immediately after the connection is established, the pump
continues in the "command" or "remote terminal" (abbr. "RT") mode. The "command" mode is what the
remote control of the Combo uses for its direct commands (that is, delivering bolus and retrieving
the latest changes / activities from the history). The "remote terminal" mode replicates the LCD
on the pump itself along with the 4 Combo buttons.

Only a few operations are possible in the command mode. In particular, the driver uses the bolus
delivery command from the command mode, the command to retrieve a history delta, and the command
for getting the pump's current date and time. But everything else (getting basal profile, setting
TBR, getting pump status...) is done in the remote terminal mode, by emulating a user pressing
buttons. This unfortunately means that these operations are performed slowly, but there is no
other choice.

## Details about long-pressing RT buttons

As part of operations like reading the pump's profile, an emulated long RT button press is sometimes
used. Such long presses cause more rapid changes compared to multiple short button presses. A
button press is "long" when the emulated user "holds down" the button, while a short button press
equals pressing and immediately releasing the emulated button.

The greater speed of long button presses comes with a drawback though: "Overshoots" can happen. For
example, if long button pressing is used for adjusting a quantity on screen, then the quantity may
still get incremented/decremented after the emulated user "released" the button. It is therefore
necessary to check the quantity on screen, and finetune it with short button presses afterwards
if necessary.

## Idempotent and non-idempotent high level commands

A command is _idempotent_ if it can be repeated if the connection to the pump was lost. Most
commands are idempotent. For example, reading the basal profile can be repeated if during the
initial basal profile retrieval the connection was lost (for example because the user walked away
from the pump). After a few attempts to repeat the command, an error is produced (to avoid an
infinite loop).

Currently, there is only one non-idempotent command: Delivering a bolus. This one _cannot_ be
repeated, otherwise there is a high risk of infusing too much insulin. Instead, in case of a
connection failure, the delivering bolus command fails immediately and is not automatically
attempted again.

## Automatic datetime adjustments and timezone offset handling

ComboCtl automatically adjusts the date and time of the Combo. This is done through the RT mode,
since there is no command-mode command to _set_ the current datetime (but there is one for
_getting_ the current datetime). But since the Combo cannot store a timezone offset (it only stores
localtime), the timezone offset that has been used so far is stored in a dedicated field in the
pump state store that ComboCtl uses. DST changes and timezone changes can be tracked properly with
this logic.

The pump's current datetime is always retrieved (through the command mode) every time a connection
is established to it, and compared to the system's current datetime. If these two differ too much,
the pump's datetime is automatically adjusted. This keeps the pump's datetime in sync.

## Notes about how TBRs are set

TBRs are set through the remote terminal mode. The driver assumes that the Combo is configured
to use 15-minute TBR duration steps sizes and a TBR percentage maximum of 500%. There is code
in the driver to detect if the maximum is not set to 500%. If AndroidAPS tries to set a percentage
that is higher than the actually configured maximum, then eventually, an error is reported.

:warning: The duration step size cannot be detected by the driver. The user _must_ make sure that
the step size is configured to 15 minutes.

## Pairing with a Combo and the issue with pump queue connection timeouts

When pairing, the pump queue's internal timeout is likely to be reached. Essentially, the queue
tries to connect to the pump right after the driver was selected in the configuration. But
a connection cannot be established because the pump is not yet paired.

When the queue attempts to connect to the pump, it "thinks" that if the connect procedure does not
complete after 120 seconds, then the driver must be stuck somehow. The queue then hits a timeout.
The assumption about 120s is correct if the Combo is already paired (a connection should be set up
in far less time than 120s). But if it is currently being paired, the steps involved can take
about 2-3 minutes.

For this reason, the driver automatically requests a pump update - which connects to the pump -
once pairing is done.

## Changes to ComboCtl in the local copy

The code in `comboctl/` is ComboCtl minus the `jvmMain/` code, which contains code for the Linux
platform. This includes C++ glue code to the BlueZ stack. Since none of this is useful to
AndroidAPS, it is better left out, especially since it consists of almost 9000 lines of code.

Also, the original `comboctl/build.gradle.kts` files is replaced by `comboctl/build.gradle`, which
is much simpler, and builds ComboCtl as a kotlin-android project, not a Kotlin Multiplatform one.
This simplifies integration into AndroidAPS, and avoids multiplatform problems (after all,
Kotlin Multiplatform is still marked as an alpha version feature).

The `comboctl/src/androidMain/AndroidManifest.xml` file also differs in that the `ComboCtl` version
contains `package="info.nightscout.comboctl.android"` in its `<manifest>` tag, while the AndroidAPS
version doesn't.

When updating ComboCtl, it is important to keep these differences in mind.

Differences between the copy in `comboctl/` and the original ComboCtl code must be kept as little
as possible, and preferably be transferred to the main ComboCtl project. This helps with keeping the
`comboctl/` copy and the main project in sync since transferring changes then is straightforward.
